using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

/// <summary>
/// 프로필 설치: 로더(Fabric/Forge) 버전 파일 → 모드 동기화 → options.txt → 공식 런처 프로필 등록.
/// 로그인과 게임 실행은 공식 Minecraft 런처가 담당한다 (계정 정보를 다루지 않음).
/// </summary>
public sealed class Installer
{
    public const string Forge189 = "1.8.9-11.15.1.2318-1.8.9";
    private readonly IProgress<string> _log;
    private static HttpClient Http => Modrinth.Http;

    public Installer(IProgress<string> log) => _log = log;

    public async Task<string> InstallAsync(Profile p, CancellationToken ct)
    {
        Directory.CreateDirectory(p.ModsDir);
        var firstInstall = !File.Exists(Path.Combine(p.GameDir, "options.txt"));

        var versionId = p.Loader == LoaderType.Fabric
            ? await InstallFabricAsync(p.GameVersion, ct)
            : await InstallForge189Async(ct);

        await UpgradePresetAsync(p, ct);
        await SyncModsAsync(p, ct);

        if (p.ApplyPerformanceOptions && firstInstall)
        {
            Presets.ApplyPerformanceOptions(p, firstInstall: true);
            _log.Report("성능 최적화 options.txt 를 만들었습니다.");
        }

        LauncherProfiles.Register(p, versionId);
        _log.Report($"공식 런처에 'OptiClient · {p.Name}' 프로필을 등록했습니다. (버전: {versionId})");
        return versionId;
    }

    /// <summary>예전에 만든 프로필에 새로 추가된 최적화 모드를 한 번 채워 넣는다</summary>
    private async Task UpgradePresetAsync(Profile p, CancellationToken ct)
    {
        if (p.PresetVersion >= Presets.Version) return;
        // 프로필에는 slug(sodium) 나 id(AANobbMI) 가 섞여 있으므로 둘 다 비교한다
        var have = new HashSet<string>(p.Mods.Select(m => m.ProjectId), StringComparer.OrdinalIgnoreCase);
        var added = new List<string>();
        foreach (var m in Presets.OptimizationMods(p.Target))
        {
            if (have.Contains(m.ProjectId)) continue;
            string? id = null;
            try { id = await Modrinth.GetProjectIdAsync(m.ProjectId, ct); } catch (HttpRequestException) { }
            if (id != null && have.Contains(id)) continue;
            p.Mods.Add(m);
            added.Add(m.Title);
        }
        p.PresetVersion = Presets.Version;
        if (added.Count > 0) _log.Report("빠져 있던 최적화 모드를 추가했어요: " + string.Join(", ", added));
    }

    // ---------------- Fabric ----------------

    private async Task<string> InstallFabricAsync(string game, CancellationToken ct)
    {
        _log.Report($"Fabric 로더 확인 중 ({game})...");
        var loaders = JsonNode.Parse(await Http.GetStringAsync($"https://meta.fabricmc.net/v2/versions/loader/{game}", ct))!.AsArray();
        var loader = loaders.FirstOrDefault(l => (bool?)l!["loader"]!["stable"] == true) ?? loaders[0]!;
        var loaderVersion = (string)loader["loader"]!["version"]!;

        var json = await Http.GetStringAsync($"https://meta.fabricmc.net/v2/versions/loader/{game}/{loaderVersion}/profile/json", ct);
        var id = (string)JsonNode.Parse(json)!["id"]!;
        var dir = Path.Combine(Paths.MinecraftDir, "versions", id);
        var file = Path.Combine(dir, id + ".json");
        if (!File.Exists(file))
        {
            Directory.CreateDirectory(dir);
            await File.WriteAllTextAsync(file, json, ct);
            _log.Report($"Fabric {loaderVersion} 설치 완료.");
        }
        else
        {
            _log.Report($"Fabric {loaderVersion} 이미 설치됨.");
        }
        return id;
    }

    // ---------------- Forge 1.8.9 ----------------

    private async Task<string> InstallForge189Async(CancellationToken ct)
    {
        var versionId = $"1.8.9-forge{Forge189}";
        var versionJson = Path.Combine(Paths.MinecraftDir, "versions", versionId, versionId + ".json");
        var forgeJar = Path.Combine(Paths.MinecraftDir, "libraries", MavenPath($"net.minecraftforge:forge:{Forge189}"));
        if (File.Exists(versionJson) && File.Exists(forgeJar))
        {
            _log.Report("Forge 1.8.9 이미 설치됨.");
            return versionId;
        }

        _log.Report("Forge 1.8.9 설치 프로그램 내려받는 중...");
        var installer = Path.Combine(Paths.CacheDir, $"forge-{Forge189}-installer.jar");
        if (!File.Exists(installer))
        {
            await Modrinth.DownloadAsync(
                $"https://maven.minecraftforge.net/net/minecraftforge/forge/{Forge189}/forge-{Forge189}-installer.jar",
                installer, ct);
        }

        using var zip = ZipFile.OpenRead(installer);
        var profileEntry = zip.GetEntry("install_profile.json") ?? throw new InvalidDataException("install_profile.json 이 없습니다.");
        JsonNode profile;
        using (var s = profileEntry.Open()) profile = (await JsonNode.ParseAsync(s, cancellationToken: ct))!;

        var install = profile["install"]!;
        var versionInfo = profile["versionInfo"]!.AsObject();
        versionId = (string)versionInfo["id"]!;
        versionJson = Path.Combine(Paths.MinecraftDir, "versions", versionId, versionId + ".json");

        // 1) forge universal jar → libraries
        forgeJar = Path.Combine(Paths.MinecraftDir, "libraries", MavenPath((string)install["path"]!));
        Directory.CreateDirectory(Path.GetDirectoryName(forgeJar)!);
        zip.GetEntry((string)install["filePath"]!)!.ExtractToFile(forgeJar, true);

        // 2) 필요한 라이브러리 내려받기 (옛 Forge 설치 프로그램이 하던 일)
        foreach (var lib in versionInfo["libraries"]!.AsArray())
        {
            var name = (string)lib!["name"]!;
            if (name.StartsWith("net.minecraftforge:forge:", StringComparison.Ordinal)) continue;
            if (lib["clientreq"] is JsonValue cr && cr.GetValue<bool>() == false) continue;
            var dest = Path.Combine(Paths.MinecraftDir, "libraries", MavenPath(name));
            if (File.Exists(dest)) continue;
            await DownloadLibraryAsync(name, (string?)lib["url"], dest, ct);
        }

        // 3) 버전 json
        Directory.CreateDirectory(Path.GetDirectoryName(versionJson)!);
        await File.WriteAllTextAsync(versionJson, versionInfo.ToJsonString(Json.Pretty), ct);
        _log.Report("Forge 1.8.9 설치 완료.");
        return versionId;
    }

    private async Task DownloadLibraryAsync(string name, string? baseUrl, string dest, CancellationToken ct)
    {
        var path = MavenPath(name).Replace('\\', '/');
        var bases = new List<string>();
        if (!string.IsNullOrEmpty(baseUrl))
            bases.Add(baseUrl.Replace("http://files.minecraftforge.net/maven/", "https://maven.minecraftforge.net/"));
        bases.AddRange(new[]
        {
            "https://libraries.minecraft.net/",
            "https://maven.minecraftforge.net/",
            "https://repo1.maven.org/maven2/",
        });
        foreach (var b in bases.Distinct())
        {
            try
            {
                await Modrinth.DownloadAsync(b.TrimEnd('/') + "/" + path, dest, ct);
                _log.Report($"  라이브러리: {name}");
                return;
            }
            catch (HttpRequestException)
            {
                // 다음 저장소 시도
            }
        }
        _log.Report($"  (경고) 라이브러리를 받지 못함: {name} — 공식 런처가 대신 받을 수 있습니다.");
    }

    /// <summary>group:artifact:version[:classifier] → group/path/artifact/version/artifact-version.jar</summary>
    public static string MavenPath(string coord)
    {
        var parts = coord.Split(':');
        var group = parts[0].Replace('.', Path.DirectorySeparatorChar);
        var file = parts.Length > 3 ? $"{parts[1]}-{parts[2]}-{parts[3]}.jar" : $"{parts[1]}-{parts[2]}.jar";
        return Path.Combine(group, parts[1], parts[2], file);
    }

    // ---------------- 모드 ----------------

    private async Task SyncModsAsync(Profile p, CancellationToken ct)
    {
        var known = new HashSet<string>(p.Mods.Select(m => m.ProjectId));
        var queue = new Queue<ModEntry>(p.Mods);

        while (queue.Count > 0)
        {
            ct.ThrowIfCancellationRequested();
            var m = queue.Dequeue();
            switch (m.Source)
            {
                case ModSource.Bundled:
                    InstallBundledHud(p, m);
                    break;

                case ModSource.Modrinth:
                    if (m.FileName != null && ModFileExists(p, m.FileName)) break;
                    m.Status = "내려받는 중";
                    var file = await Modrinth.ResolveAsync(m.ProjectId, p.Target, ct);
                    if (file == null)
                    {
                        m.Status = "이 버전용 파일 없음";
                        _log.Report($"(건너뜀) {m.Title}: {p.Target.Label} 용 파일이 Modrinth 에 없습니다.");
                        break;
                    }
                    await Modrinth.DownloadAsync(file.Url, Path.Combine(p.ModsDir, file.FileName), ct);
                    known.Remove(m.ProjectId);
                    m.ProjectId = file.ProjectId; // slug → 실제 ID (의존성 중복 방지)
                    known.Add(m.ProjectId);
                    m.FileName = file.FileName;
                    m.Status = "";
                    _log.Report($"모드 설치: {m.Title} ({file.FileName})");

                    foreach (var dep in file.RequiredDependencies.Where(d => !known.Contains(d)))
                    {
                        var title = await Modrinth.GetTitleAsync(dep, ct);
                        if (p.Mods.Any(x => string.Equals(x.Title, title, StringComparison.OrdinalIgnoreCase))) continue;
                        var entry = new ModEntry { ProjectId = dep, Title = title, Source = ModSource.Modrinth, IsDependency = true };
                        known.Add(dep);
                        p.Mods.Add(entry);
                        queue.Enqueue(entry);
                        _log.Report($"  필요한 모드 추가: {title}");
                    }
                    break;
            }
            ApplyEnabled(p, m);
        }
    }

    private void InstallBundledHud(Profile p, ModEntry m)
    {
        var src = Directory.Exists(Paths.BundledHudDir)
            ? Directory.GetFiles(Paths.BundledHudDir, $"opticlient-hud-{p.GameVersion}-*.jar")
                .Where(f => !f.EndsWith("-dev.jar") && !f.EndsWith("-sources.jar"))
                .OrderByDescending(f => f).FirstOrDefault()
            : null;
        if (src == null)
        {
            m.Status = "HUD jar 없음";
            _log.Report($"(경고) {Paths.BundledHudDir} 에 {p.GameVersion} 용 HUD 모드가 없습니다. build-all.ps1 을 먼저 실행하세요.");
            return;
        }
        foreach (var old in Directory.GetFiles(p.ModsDir, "opticlient-hud-*"))
            File.Delete(old);
        var name = Path.GetFileName(src);
        File.Copy(src, Path.Combine(p.ModsDir, name), true);
        m.FileName = name;
        m.Status = "";
        _log.Report($"HUD 모드 복사: {name}");
    }

    private static bool ModFileExists(Profile p, string fileName) =>
        File.Exists(Path.Combine(p.ModsDir, fileName)) || File.Exists(Path.Combine(p.ModsDir, fileName + ".disabled"));

    /// <summary>꺼진 모드는 .jar.disabled 로 이름을 바꿔 로더가 읽지 않게 한다.</summary>
    public static void ApplyEnabled(Profile p, ModEntry m)
    {
        if (m.FileName == null) return;
        var on = Path.Combine(p.ModsDir, m.FileName);
        var off = on + ".disabled";
        if (m.Enabled && File.Exists(off)) File.Move(off, on, true);
        else if (!m.Enabled && File.Exists(on)) File.Move(on, off, true);
    }

    public static void DeleteModFile(Profile p, ModEntry m)
    {
        if (m.FileName == null) return;
        var on = Path.Combine(p.ModsDir, m.FileName);
        if (File.Exists(on)) File.Delete(on);
        if (File.Exists(on + ".disabled")) File.Delete(on + ".disabled");
    }

    // ---------------- 공식 런처 실행 ----------------

    public static bool IsOfficialLauncherRunning() =>
        Process.GetProcessesByName("MinecraftLauncher").Length > 0 ||
        Process.GetProcessesByName("Minecraft").Any(IsLauncherProcess);

    private static bool IsLauncherProcess(Process proc)
    {
        try
        {
            var file = proc.MainModule?.FileName ?? "";
            return file.Contains("Minecraft Launcher", StringComparison.OrdinalIgnoreCase) ||
                   file.Contains("Microsoft.4297127D64EC6", StringComparison.OrdinalIgnoreCase);
        }
        catch
        {
            return true; // 경로를 못 읽으면 런처로 간주 (보수적으로)
        }
    }

    /// <summary>
    /// 공식 런처를 닫는다: 먼저 창에 닫기 요청(X 버튼과 같음)을 보내고,
    /// 창은 다 사라졌는데 프로세스만 남아 있으면 (그리고 공식 런처 자바로 켠 게임이 없으면) 남은 프로세스를 정리한다.
    /// 다 닫히면 true
    /// </summary>
    public static bool CloseOfficialLauncher(TimeSpan timeout)
    {
        OfficialAutoPlay.RequestCloseLauncher();
        var until = DateTime.UtcNow + timeout;
        while (DateTime.UtcNow < until)
        {
            if (!IsOfficialLauncherRunning()) return true;
            Thread.Sleep(300);
        }
        if (!IsOfficialLauncherRunning()) return true;
        if (OfficialAutoPlay.HasLauncherWindow() || IsOfficialJavaRunning()) return false;

        foreach (var proc in Process.GetProcessesByName("MinecraftLauncher").Concat(Process.GetProcessesByName("Minecraft").Where(IsLauncherProcess)))
            try { proc.Kill(); } catch { /* 이미 종료 */ }
        for (var i = 0; i < 20 && IsOfficialLauncherRunning(); i++) Thread.Sleep(250);
        return !IsOfficialLauncherRunning();
    }

    /// <summary>공식 런처 프로세스를 종료하고 종료한 개수를 돌려준다 (게임 프로세스는 건드리지 않음)</summary>
    public static int KillOfficialLauncher()
    {
        var n = 0;
        foreach (var proc in Process.GetProcessesByName("MinecraftLauncher").Concat(Process.GetProcessesByName("Minecraft").Where(IsLauncherProcess)))
        {
            try
            {
                proc.Kill();
                n++;
            }
            catch { /* 이미 종료 */ }
        }
        return n;
    }

    /// <summary>공식 런처의 자바(런타임)로 돌고 있는 게임이 있는지 (있으면 런처를 건드리지 않는다)</summary>
    private static bool IsOfficialJavaRunning()
    {
        foreach (var proc in Process.GetProcessesByName("javaw").Concat(Process.GetProcessesByName("java")))
        {
            try
            {
                var file = proc.MainModule?.FileName ?? "";
                if (file.Contains("Microsoft.4297127D64EC6", StringComparison.OrdinalIgnoreCase) ||
                    file.Contains(@"\.minecraft\runtime", StringComparison.OrdinalIgnoreCase) ||
                    file.Contains(@"Minecraft Launcher\runtime", StringComparison.OrdinalIgnoreCase))
                    return true;
            }
            catch
            {
                return true; // 경로를 못 읽으면 게임일 수 있으니 보수적으로
            }
        }
        return false;
    }

    public static bool StartOfficialLauncher()
    {
        var pf86 = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFilesX86);
        var pf = Environment.GetFolderPath(Environment.SpecialFolder.ProgramFiles);
        string[] exes =
        {
            Path.Combine(pf86, "Minecraft Launcher", "MinecraftLauncher.exe"),
            Path.Combine(pf, "Minecraft Launcher", "MinecraftLauncher.exe"),
        };
        foreach (var exe in exes)
        {
            if (!File.Exists(exe)) continue;
            Process.Start(new ProcessStartInfo(exe) { UseShellExecute = true });
            return true;
        }

        // Microsoft Store / Xbox 앱 버전
        try
        {
            Process.Start(new ProcessStartInfo("explorer.exe", @"shell:AppsFolder\Microsoft.4297127D64EC6_8wekyb3d8bbwe!Minecraft") { UseShellExecute = true });
            return true;
        }
        catch
        {
            return false;
        }
    }
}
