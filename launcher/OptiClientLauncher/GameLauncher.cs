using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Text;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace OptiClientLauncher;

/// <summary>
/// 공식 런처 없이 게임을 직접 실행한다.
/// 버전 json(상속 합치기) → Java 런타임 → 게임 jar·라이브러리·리소스 → 실행 인수 → 프로세스 시작.
/// 파일은 공식 런처와 같은 .minecraft 폴더(versions/libraries/assets)를 함께 쓴다.
/// </summary>
public sealed class GameLauncher
{
    private const string VersionManifest = "https://piston-meta.mojang.com/mc/game/version_manifest_v2.json";
    private const string RuntimeIndex = "https://launchermeta.mojang.com/v1/products/java-runtime/2ec0cc96c44e5a76b9c8b7c39df7210883d12871/all.json";
    private const string AssetsBase = "https://resources.download.minecraft.net/";

    private readonly IProgress<string> _log;
    private static HttpClient Http => Modrinth.Http;
    private static string Mc => Paths.MinecraftDir;

    public GameLauncher(IProgress<string> log) => _log = log;

    // =====================================================================
    // 버전 json
    // =====================================================================

    private sealed class ResolvedVersion
    {
        public string Id = "";
        public string MainClass = "";
        public string? LegacyArgs;
        public readonly List<JsonNode> GameArgs = new();
        public readonly List<JsonNode> JvmArgs = new();
        public readonly List<JsonObject> Libraries = new();
        public JsonObject? AssetIndex;
        public string JavaComponent = "jre-legacy";
        public JsonObject? ClientDownload;
        public string JarId = "";
    }

    private async Task<JsonObject> LoadVersionJsonAsync(string id, CancellationToken ct)
    {
        var file = Path.Combine(Mc, "versions", id, id + ".json");
        if (!File.Exists(file))
        {
            _log.Report($"버전 정보 내려받는 중: {id}");
            var manifest = JsonNode.Parse(await Http.GetStringAsync(VersionManifest, ct))!;
            var entry = manifest["versions"]!.AsArray().FirstOrDefault(v => (string?)v!["id"] == id)
                        ?? throw new InvalidOperationException($"마인크래프트 버전 '{id}' 을 찾지 못했습니다.");
            await Modrinth.DownloadAsync((string)entry["url"]!, file, ct);
        }
        return JsonNode.Parse(await File.ReadAllTextAsync(file, ct))!.AsObject();
    }

    /// <summary>inheritsFrom 을 따라가며 (Fabric/Forge → 바닐라) 하나로 합친다. 자식 값이 우선.</summary>
    private async Task<ResolvedVersion> ResolveAsync(string id, CancellationToken ct)
    {
        var chain = new List<JsonObject>();
        string? next = id;
        while (next != null)
        {
            var json = await LoadVersionJsonAsync(next, ct);
            chain.Add(json);
            next = (string?)json["inheritsFrom"];
        }

        var v = new ResolvedVersion { Id = id };
        var seen = new HashSet<string>();
        // 라이브러리: 자식 먼저 (같은 라이브러리는 자식 것을 쓴다)
        foreach (var json in chain)
        {
            foreach (var lib in json["libraries"]?.AsArray() ?? new JsonArray())
            {
                if (lib is not JsonObject o) continue;
                if (seen.Add(LibraryKey((string)o["name"]!))) v.Libraries.Add(o);
            }
        }
        // 나머지: 부모부터 덮어쓰기
        for (int i = chain.Count - 1; i >= 0; i--)
        {
            var json = chain[i];
            if (json["mainClass"] is JsonValue mc) v.MainClass = (string)mc!;
            if (json["minecraftArguments"] is JsonValue la) v.LegacyArgs = (string)la!;
            if (json["arguments"]?["game"] is JsonArray ga) v.GameArgs.AddRange(ga.Select(n => n!.DeepClone()));
            if (json["arguments"]?["jvm"] is JsonArray ja) v.JvmArgs.AddRange(ja.Select(n => n!.DeepClone()));
            if (json["assetIndex"] is JsonObject ai) v.AssetIndex = ai;
            if (json["javaVersion"]?["component"] is JsonValue jc) v.JavaComponent = (string)jc!;
            if (json["downloads"]?["client"] is JsonObject cd) v.ClientDownload = cd;
        }
        v.JarId = (string?)chain[0]["jar"] ?? (string)chain[^1]["id"]!;
        return v;
    }

    /// <summary>group:artifact[:classifier] (버전 제외) — 같은 라이브러리 판별용</summary>
    private static string LibraryKey(string name)
    {
        var p = name.Split(':');
        return p.Length > 3 ? $"{p[0]}:{p[1]}:{p[3]}" : $"{p[0]}:{p[1]}";
    }

    // =====================================================================
    // 규칙 (OS·기능 조건)
    // =====================================================================

    private static bool Allowed(JsonNode? rules)
    {
        if (rules is not JsonArray arr) return true;
        bool allow = false;
        foreach (var r in arr)
        {
            bool match = true;
            if (r?["os"] is JsonObject os)
            {
                if (os["name"] is JsonValue n && (string)n! != "windows") match = false;
                if (os["arch"] is JsonValue a && (string)a! == "x86" && Environment.Is64BitOperatingSystem) match = false;
            }
            if (r?["features"] is JsonObject) match = false; // 데모·해상도·빠른 실행 같은 특수 기능은 쓰지 않음
            if (match) allow = (string?)r?["action"] == "allow";
        }
        return allow;
    }

    // =====================================================================
    // 내려받기
    // =====================================================================

    private sealed record FileJob(string Url, string Path, long Size);

    private async Task DownloadManyAsync(List<FileJob> jobs, string what, CancellationToken ct)
    {
        var todo = jobs.Where(j => !File.Exists(j.Path) || (j.Size > 0 && new FileInfo(j.Path).Length != j.Size))
                       .GroupBy(j => j.Path).Select(g => g.First()).ToList();
        if (todo.Count == 0) return;
        _log.Report($"{what} {todo.Count}개 내려받는 중...");
        int done = 0, failed = 0;
        using var gate = new SemaphoreSlim(16);
        await Task.WhenAll(todo.Select(async j =>
        {
            await gate.WaitAsync(ct);
            try
            {
                for (int attempt = 0; ; attempt++)
                {
                    try
                    {
                        await Modrinth.DownloadAsync(j.Url, j.Path, ct);
                        break;
                    }
                    catch (HttpRequestException) when (attempt < 2)
                    {
                        await Task.Delay(500, ct);
                    }
                }
            }
            catch (HttpRequestException)
            {
                Interlocked.Increment(ref failed);
            }
            finally
            {
                gate.Release();
                var n = Interlocked.Increment(ref done);
                if (n % 200 == 0 || n == todo.Count) _log.Report($"  {what}: {n}/{todo.Count}");
            }
        }));
        if (failed > 0) _log.Report($"  (경고) {what} {failed}개를 받지 못했습니다.");
    }

    // ---------------- Java ----------------

    /// <summary>버전에 맞는 Java (공식 런처가 받아 둔 것이 있으면 그대로 쓰고, 없으면 Mojang 런타임을 받는다)</summary>
    private async Task<string> EnsureJavaAsync(string component, CancellationToken ct)
    {
        // 참고: 1.8.9 를 최신 Java 8(Temurin 8u512)로 실행해 보았지만 Mojang 기본 Java 8(8u51)보다 FPS 가 낮게 나와서 쓰지 않는다.
        var local = Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData);
        string[] candidates =
        {
            Path.Combine(Paths.Root, "runtime", component, "bin", "javaw.exe"),
            Path.Combine(local, "Packages", "Microsoft.4297127D64EC6_8wekyb3d8bbwe", "LocalCache", "Local", "runtime", component, "windows-x64", component, "bin", "javaw.exe"),
            Path.Combine(Mc, "runtime", component, "windows-x64", component, "bin", "javaw.exe"),
        };
        foreach (var c in candidates)
            if (File.Exists(c)) return c;

        _log.Report($"Java 런타임({component}) 내려받는 중...");
        var index = JsonNode.Parse(await Http.GetStringAsync(RuntimeIndex, ct))!;
        var entry = index["windows-x64"]?[component]?.AsArray().FirstOrDefault()
                    ?? throw new InvalidOperationException($"Java 런타임 '{component}' 을 찾지 못했습니다.");
        var manifest = JsonNode.Parse(await Http.GetStringAsync((string)entry["manifest"]!["url"]!, ct))!;
        var root = Path.Combine(Paths.Root, "runtime", component);
        var jobs = new List<FileJob>();
        foreach (var (path, info) in manifest["files"]!.AsObject())
        {
            var dest = Path.Combine(root, path.Replace('/', Path.DirectorySeparatorChar));
            switch ((string?)info!["type"])
            {
                case "directory":
                    Directory.CreateDirectory(dest);
                    break;
                case "file":
                    var raw = info["downloads"]!["raw"]!;
                    jobs.Add(new FileJob((string)raw["url"]!, dest, (long?)raw["size"] ?? 0));
                    break;
            }
        }
        await DownloadManyAsync(jobs, "Java 파일", ct);
        var java = Path.Combine(root, "bin", "javaw.exe");
        if (!File.Exists(java)) throw new InvalidOperationException("Java 런타임 설치에 실패했습니다.");
        return java;
    }

    // ---------------- 게임 jar / 라이브러리 ----------------

    private sealed record LibFile(string Path, string? Url, long Size, bool IsNative, JsonArray? Exclude);

    private static IEnumerable<LibFile> LibraryFiles(JsonObject lib)
    {
        if (!Allowed(lib["rules"])) yield break;
        if (lib["clientreq"] is JsonValue cr && cr.GetValue<bool>() == false) yield break;
        var name = (string)lib["name"]!;

        // 옛 방식 natives (1.8.9 등): 압축을 풀어 natives 폴더에 넣는다
        if (lib["natives"]?["windows"] is JsonValue nat)
        {
            var classifier = ((string)nat!).Replace("${arch}", Environment.Is64BitOperatingSystem ? "64" : "32");
            if (lib["downloads"]?["classifiers"]?[classifier] is JsonObject c)
                yield return new LibFile(LibPath((string)c["path"]!), (string?)c["url"], (long?)c["size"] ?? 0, true, lib["extract"]?["exclude"] as JsonArray);
            else
                yield return new LibFile(Path.Combine(Mc, "libraries", Installer.MavenPath(name + ":" + classifier)), MavenUrl(lib, name + ":" + classifier), 0, true, lib["extract"]?["exclude"] as JsonArray);
        }

        if (lib["downloads"] is JsonObject downloads)
        {
            if (downloads["artifact"] is JsonObject art)
                yield return new LibFile(LibPath((string)art["path"]!), (string?)art["url"], (long?)art["size"] ?? 0, false, null);
        }
        else
        {
            yield return new LibFile(Path.Combine(Mc, "libraries", Installer.MavenPath(name)), MavenUrl(lib, name), (long?)lib["size"] ?? 0, false, null);
        }
    }

    private static string LibPath(string rel) => Path.Combine(Mc, "libraries", rel.Replace('/', Path.DirectorySeparatorChar));

    private static string MavenUrl(JsonObject lib, string coord)
    {
        var baseUrl = (string?)lib["url"] ?? "https://libraries.minecraft.net/";
        baseUrl = baseUrl.Replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/");
        return baseUrl.TrimEnd('/') + "/" + Installer.MavenPath(coord).Replace('\\', '/');
    }

    // ---------------- 리소스(assets) ----------------

    private async Task<string> EnsureAssetsAsync(ResolvedVersion v, CancellationToken ct)
    {
        if (v.AssetIndex == null) return "legacy";
        var id = (string)v.AssetIndex["id"]!;
        var indexFile = Path.Combine(Mc, "assets", "indexes", id + ".json");
        if (!File.Exists(indexFile)) await Modrinth.DownloadAsync((string)v.AssetIndex["url"]!, indexFile, ct);
        var index = JsonNode.Parse(await File.ReadAllTextAsync(indexFile, ct))!;
        var jobs = new List<FileJob>();
        foreach (var (_, obj) in index["objects"]!.AsObject())
        {
            var hash = (string)obj!["hash"]!;
            var sub = hash[..2];
            jobs.Add(new FileJob(AssetsBase + sub + "/" + hash, Path.Combine(Mc, "assets", "objects", sub, hash), (long?)obj["size"] ?? 0));
        }
        await DownloadManyAsync(jobs, "리소스", ct);
        return id;
    }

    // =====================================================================
    // 실행
    // =====================================================================

    public async Task<Process> LaunchAsync(Profile p, string versionId, Account account, CancellationToken ct)
    {
        _log.Report($"직접 실행 준비: {versionId} ({account.Name}, {(account.IsOffline ? "오프라인" : "Microsoft")})");
        var v = await ResolveAsync(versionId, ct);
        var component = Environment.GetEnvironmentVariable("OPTICLIENT_JAVA") is { Length: > 0 } forced ? forced
                        : p.JavaRuntime.Length > 0 ? p.JavaRuntime : v.JavaComponent;
        if (component != v.JavaComponent) _log.Report($"Java: {v.JavaComponent} 대신 {component} 사용");
        var java = await EnsureJavaAsync(component, ct);

        // 게임 jar
        var clientJar = Path.Combine(Mc, "versions", v.JarId, v.JarId + ".jar");
        var jobs = new List<FileJob>();
        if (v.ClientDownload != null)
            jobs.Add(new FileJob((string)v.ClientDownload["url"]!, clientJar, (long?)v.ClientDownload["size"] ?? 0));

        // 라이브러리
        var classpath = new List<string>();
        var natives = new List<LibFile>();
        foreach (var lib in v.Libraries)
        {
            foreach (var f in LibraryFiles(lib))
            {
                if (f.Url != null) jobs.Add(new FileJob(f.Url, f.Path, f.Size));
                if (f.IsNative) natives.Add(f);
                else if (!classpath.Contains(f.Path)) classpath.Add(f.Path);
            }
        }
        classpath.Add(clientJar);
        await DownloadManyAsync(jobs, "게임 파일", ct);

        // 옛 방식 natives 압축 풀기
        var nativesDir = Path.Combine(Paths.Root, "natives", versionId);
        Directory.CreateDirectory(nativesDir);
        foreach (var n in natives.Where(n => File.Exists(n.Path)))
        {
            var exclude = n.Exclude?.Select(e => (string)e!).ToList() ?? new List<string>();
            using var zip = ZipFile.OpenRead(n.Path);
            foreach (var entry in zip.Entries)
            {
                if (entry.FullName.EndsWith('/') || exclude.Any(e => entry.FullName.StartsWith(e, StringComparison.Ordinal))) continue;
                var dest = Path.Combine(nativesDir, entry.FullName);
                if (File.Exists(dest)) continue;
                Directory.CreateDirectory(Path.GetDirectoryName(dest)!);
                entry.ExtractToFile(dest);
            }
        }

        var assetsId = await EnsureAssetsAsync(v, ct);
        foreach (var missing in classpath.Where(c => !File.Exists(c)).Take(3))
            _log.Report($"  (경고) 파일 없음: {missing}");

        // ---------------- 인수 ----------------
        var vars = new Dictionary<string, string>
        {
            ["auth_player_name"] = account.Name,
            ["version_name"] = versionId,
            ["game_directory"] = p.GameDir,
            ["assets_root"] = Path.Combine(Mc, "assets"),
            ["game_assets"] = Path.Combine(Mc, "assets"),
            ["assets_index_name"] = assetsId,
            ["auth_uuid"] = account.Uuid,
            ["auth_access_token"] = account.AccessToken,
            ["auth_session"] = account.AccessToken,
            ["clientid"] = "",
            ["auth_xuid"] = account.Xuid,
            ["user_type"] = account.IsOffline ? "legacy" : "msa",
            ["version_type"] = "OptiClient",
            ["user_properties"] = "{}",
            ["natives_directory"] = nativesDir,
            ["launcher_name"] = "OptiClient",
            ["launcher_version"] = "1.0",
            ["classpath"] = string.Join(";", classpath),
            ["library_directory"] = Path.Combine(Mc, "libraries"),
            ["classpath_separator"] = ";",
        };

        var args = new List<string>
        {
            $"-Xmx{p.MemoryMb}M",
            $"-Xms{Math.Min(p.MemoryMb, 1024)}M",
            "-XX:+UnlockExperimentalVMOptions", "-XX:+UseG1GC", "-XX:G1NewSizePercent=20", "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50", "-XX:G1HeapRegionSize=32M",
            // 참조 정리를 병렬로, 모드가 부르는 System.gc() 무시 (멈칫거림 줄이기)
            "-XX:+ParallelRefProcEnabled", "-XX:+DisableExplicitGC",
            // 게임 출력(한글)을 런처가 UTF-8 로 읽을 수 있게 (Java 17 이하는 sun.*, 18 이상은 stdout.*)
            "-Dsun.stdout.encoding=UTF-8", "-Dsun.stderr.encoding=UTF-8", "-Dstdout.encoding=UTF-8", "-Dstderr.encoding=UTF-8",
        };
        // Java 17 이하는 로그(Log4j)가 시스템 인코딩(한국어 Windows = CP949)으로 나가므로 UTF-8 로 맞춘다 (18 이상은 원래 UTF-8)
        args.Add("-Dfile.encoding=UTF-8");
        args.AddRange(p.ExtraJvmArgs.Split(' ', StringSplitOptions.RemoveEmptyEntries));
        if (v.JvmArgs.Count > 0) args.AddRange(Expand(v.JvmArgs, vars));
        else args.AddRange(new[] { "-Djava.library.path=" + nativesDir, "-cp", vars["classpath"] });
        args.Add(v.MainClass);
        if (v.GameArgs.Count > 0) args.AddRange(Expand(v.GameArgs, vars));
        else if (v.LegacyArgs != null) args.AddRange(v.LegacyArgs.Split(' ', StringSplitOptions.RemoveEmptyEntries).Select(a => Fill(a, vars)));
        if (p.WindowWidth > 0 && p.WindowHeight > 0)
            args.AddRange(new[] { "--width", p.WindowWidth.ToString(), "--height", p.WindowHeight.ToString() });

        Directory.CreateDirectory(p.GameDir);
        var psi = new ProcessStartInfo(java)
        {
            WorkingDirectory = p.GameDir,
            UseShellExecute = false,
            RedirectStandardOutput = true,
            RedirectStandardError = true,
            CreateNoWindow = true,
            StandardOutputEncoding = Encoding.UTF8,
            StandardErrorEncoding = Encoding.UTF8,
        };
        foreach (var a in args) psi.ArgumentList.Add(a);

        // 실행 로그 (토큰은 가린다)
        var logDir = Path.Combine(Paths.Root, "logs");
        Directory.CreateDirectory(logDir);
        var logFile = Path.Combine(logDir, $"{p.Id}-latest.log");
        var masked = string.Join(" ", args.Select(a => a == account.AccessToken && !account.IsOffline ? "******" : a));
        await File.WriteAllTextAsync(logFile, $"# {DateTime.Now}\n# {java} {masked}\n\n", ct);

        var proc = Process.Start(psi) ?? throw new InvalidOperationException("게임을 시작하지 못했습니다.");
        var writer = new StreamWriter(logFile, append: true) { AutoFlush = true };
        int shown = 0;
        void OnLine(string? line)
        {
            if (line == null) return;
            lock (writer) writer.WriteLine(line);
            // 오프라인 계정은 Mojang 인증이 당연히 실패하므로 (싱글플레이에는 영향 없음) 런처 로그에는 띄우지 않는다
            if (account.IsOffline && (line.Contains("authentication") || line.Contains("authlib") || line.Contains("Status: 401")
                                      || line.Contains("profile key") || line.Contains("Credentials"))) return;
            if (shown < 40 && (line.Contains("ERROR") || line.Contains("Exception") || line.Contains("Caused by")))
            {
                shown++;
                _log.Report("[게임] " + (line.Length > 220 ? line[..220] + "..." : line));
            }
        }
        proc.OutputDataReceived += (_, e) => OnLine(e.Data);
        proc.ErrorDataReceived += (_, e) => OnLine(e.Data);
        proc.BeginOutputReadLine();
        proc.BeginErrorReadLine();
        proc.EnableRaisingEvents = true;
        proc.Exited += (_, _) =>
        {
            lock (writer) writer.Dispose();
            _log.Report(proc.ExitCode == 0
                ? "게임이 종료됐습니다."
                : $"게임이 오류로 종료됐습니다 (코드 {proc.ExitCode}). 전체 로그: {logFile}");
        };
        _log.Report($"게임을 실행했습니다. (Java: {Path.GetFileName(Path.GetDirectoryName(Path.GetDirectoryName(java)))}, 로그: {logFile})");
        return proc;
    }

    private static readonly Regex VarPattern = new(@"\$\{([a-zA-Z_]+)\}", RegexOptions.Compiled);

    private static string Fill(string s, Dictionary<string, string> vars) =>
        VarPattern.Replace(s, m => vars.TryGetValue(m.Groups[1].Value, out var v) ? v : m.Value);

    /// <summary>arguments 배열: 문자열 또는 {rules, value} 객체</summary>
    private static IEnumerable<string> Expand(List<JsonNode> list, Dictionary<string, string> vars)
    {
        foreach (var node in list)
        {
            if (node is JsonValue s)
            {
                yield return Fill((string)s!, vars);
            }
            else if (node is JsonObject o && Allowed(o["rules"]))
            {
                if (o["value"] is JsonValue one) yield return Fill((string)one!, vars);
                else if (o["value"] is JsonArray many)
                    foreach (var x in many) yield return Fill((string)x!, vars);
            }
        }
    }
}
