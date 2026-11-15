using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;
using System.Text.RegularExpressions;

namespace OptiClientLauncher;

/// <summary>가져올 수 있는 다른 런처의 게임 폴더 하나 (또는 모드팩 파일)</summary>
public sealed class ImportSource
{
    public required string Name { get; init; }
    /// <summary>어느 런처인지 (공식 런처, Prism, Modrinth App ...)</summary>
    public required string Launcher { get; init; }
    /// <summary>mods·config·options.txt 가 들어 있는 게임 폴더</summary>
    public required string GameDir { get; set; }
    /// <summary>게임 설정 파일 (없으면 null)</summary>
    public string? OptionsFile { get; set; }
    /// <summary>Lunar Client 의 optionsLC.txt (JSON 형식)</summary>
    public bool LunarOptions { get; init; }
    /// <summary>모드를 가져올 수 있는 폴더인지 (Lunar 는 자체 모드라 설정만)</summary>
    public bool HasMods { get; init; } = true;
    public string? Version { get; set; }
    /// <summary>fabric / forge / neoforge / quilt / vanilla / null(모름)</summary>
    public string? Loader { get; set; }
    public int ModCount { get; set; }
    /// <summary>모드팩 파일(.mrpack) 이면 그 경로 (가져올 때 풀어서 GameDir 을 채운다)</summary>
    public string? PackFile { get; init; }
    /// <summary>PvP 클라이언트(Lunar·Feather·Badlion·Silent) 모드 프로필이면 그 정보</summary>
    public ClientHud? Hud { get; init; }

    /// <summary>OptiClient 가 그대로 실행할 수 있는 버전·로더면 그 대상</summary>
    public GameTarget? Target =>
        Version == null ? null : GameTarget.All.FirstOrDefault(t => t.Version == Version &&
            (t.Loader == LoaderType.Fabric ? Loader == "fabric" : Loader is "forge"));

    public string LoaderLabel => Loader switch
    {
        "fabric" => "Fabric",
        "forge" => "Forge",
        "neoforge" => "NeoForge",
        "quilt" => "Quilt",
        "vanilla" => "바닐라",
        _ => "로더 모름",
    };

    public string Detail
    {
        get
        {
            var parts = new List<string> { Version ?? "버전 모름" };
            if (HasMods && Loader != null) parts.Add(LoaderLabel);
            if (HasMods && ModCount > 0) parts.Add($"모드 {ModCount}개");
            if (PackFile != null) parts.Add("모드팩 파일");
            if (Hud != null) parts[0] = "클라이언트 프로필";
            return string.Join(" · ", parts);
        }
    }

    public string Badge => Hud != null ? "HUD·모드 설정 그대로" : !HasMods || ModCount == 0 ? "설정 가져오기" : Target != null ? "모드까지 가져오기" : "설정만 (지원 안 하는 버전)";
    public bool CanImportMods => HasMods && Target != null;
    public string Initial => Launcher.Length > 0 ? Launcher[..1] : "?";
}

public sealed class ImportOptions
{
    public bool GameSettings { get; set; } = true;
    public bool Graphics { get; set; }
    public bool Servers { get; set; } = true;
    public bool ResourcePacks { get; set; } = true;
    public bool ShaderPacks { get; set; } = true;
    public bool Mods { get; set; } = true;
    public bool Configs { get; set; } = true;
    public bool Worlds { get; set; }
    /// <summary>클라이언트 프로필의 HUD 켜짐·위치·크기와 PvP 기능</summary>
    public bool ClientHud { get; set; } = true;
}

/// <summary>
/// 다른 런처(공식 런처, Lunar, Badlion, Feather, Prism, Modrinth App, CurseForge)와 .mrpack 모드팩에서
/// 게임 설정·서버 목록·리소스팩·셰이더·모드·모드 설정을 OptiClient 프로필로 가져온다.
/// 계정·로그인 파일은 읽지 않는다 (게임 폴더의 설정·모드 파일만 본다).
/// </summary>
public static class Importer
{
    private static string AppData => Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
    private static string Home => Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);

    /// <summary>options.txt 의 version 값(데이터 버전) → 마인크래프트 버전</summary>
    private static readonly Dictionary<int, string> DataVersions = new()
    {
        [3465] = "1.20.1",
        [3955] = "1.21.1",
        [4671] = "1.21.11",
        [5023] = "26.3",
    };

    // =====================================================================
    // 찾기
    // =====================================================================

    public static List<ImportSource> Scan()
    {
        var list = new List<ImportSource>();
        void Try(Action a)
        {
            try { a(); } catch (Exception) { /* 한 런처가 이상해도 나머지는 보여 준다 */ }
        }

        var mc = Path.Combine(AppData, ".minecraft");
        Try(() =>
        {
            if (!File.Exists(Path.Combine(mc, "options.txt"))) return;
            var s = new ImportSource
            {
                Name = "공식 런처 (.minecraft)",
                Launcher = "공식 런처",
                GameDir = mc,
                OptionsFile = Path.Combine(mc, "options.txt"),
            };
            Detect(s);
            list.Add(s);
        });
        // PvP 클라이언트 모드 프로필 (Lunar · Feather · Badlion · Silent)
        Try(() => list.AddRange(ClientProfiles.Scan()));

        foreach (var root in new[] { Path.Combine(AppData, "PrismLauncher", "instances"), Path.Combine(AppData, "PolyMC", "instances") })
        {
            if (!Directory.Exists(root)) continue;
            foreach (var dir in Directory.GetDirectories(root)) Try(() => { if (Prism(dir) is { } s) list.Add(s); });
        }

        foreach (var root in new[] { Path.Combine(AppData, "ModrinthApp", "profiles"), Path.Combine(AppData, "com.modrinth.theseus", "profiles") })
        {
            if (!Directory.Exists(root)) continue;
            foreach (var dir in Directory.GetDirectories(root))
            {
                Try(() =>
                {
                    var s = new ImportSource
                    {
                        Name = Path.GetFileName(dir),
                        Launcher = "Modrinth App",
                        GameDir = dir,
                        OptionsFile = ExistingFile(Path.Combine(dir, "options.txt")),
                    };
                    Detect(s);
                    list.Add(s);
                });
            }
        }

        var cf = Path.Combine(Home, "curseforge", "minecraft", "Instances");
        if (Directory.Exists(cf))
            foreach (var dir in Directory.GetDirectories(cf)) Try(() => { if (CurseForge(dir) is { } s) list.Add(s); });

        return list;
    }

    private static string? ExistingFile(string path) => File.Exists(path) ? path : null;

    private static ImportSource? Prism(string dir)
    {
        var pack = Path.Combine(dir, "mmc-pack.json");
        if (!File.Exists(pack)) return null;
        var game = Directory.Exists(Path.Combine(dir, ".minecraft")) ? Path.Combine(dir, ".minecraft") : Path.Combine(dir, "minecraft");
        var name = Path.GetFileName(dir);
        var cfg = Path.Combine(dir, "instance.cfg");
        if (File.Exists(cfg))
        {
            var line = File.ReadLines(cfg).FirstOrDefault(l => l.StartsWith("name=", StringComparison.Ordinal));
            if (line != null && line.Length > 5) name = line[5..];
        }
        string? version = null, loader = "vanilla";
        foreach (var c in JsonNode.Parse(File.ReadAllText(pack))!["components"]!.AsArray())
        {
            var uid = (string?)c!["uid"];
            switch (uid)
            {
                case "net.minecraft": version = (string?)c["version"]; break;
                case "net.fabricmc.fabric-loader": loader = "fabric"; break;
                case "org.quiltmc.quilt-loader": loader = "quilt"; break;
                case "net.minecraftforge": loader = "forge"; break;
                case "net.neoforged": loader = "neoforge"; break;
            }
        }
        return new ImportSource
        {
            Name = name,
            Launcher = "Prism",
            GameDir = game,
            OptionsFile = ExistingFile(Path.Combine(game, "options.txt")),
            Version = version,
            Loader = loader,
            ModCount = CountMods(game),
        };
    }

    private static ImportSource? CurseForge(string dir)
    {
        var file = Path.Combine(dir, "minecraftinstance.json");
        if (!File.Exists(file)) return null;
        var j = JsonNode.Parse(File.ReadAllText(file))!;
        var loaderName = ((string?)j["baseModLoader"]?["name"] ?? "").ToLowerInvariant();
        var loader = loaderName.StartsWith("fabric") ? "fabric"
            : loaderName.StartsWith("neoforge") ? "neoforge"
            : loaderName.StartsWith("forge") ? "forge"
            : loaderName.StartsWith("quilt") ? "quilt"
            : "vanilla";
        return new ImportSource
        {
            Name = (string?)j["name"] ?? Path.GetFileName(dir),
            Launcher = "CurseForge",
            GameDir = dir,
            OptionsFile = ExistingFile(Path.Combine(dir, "options.txt")),
            Version = (string?)j["gameVersion"],
            Loader = loader,
            ModCount = CountMods(dir),
        };
    }

    /// <summary>.mrpack (Modrinth 모드팩) 파일 정보를 읽는다</summary>
    public static ImportSource ReadPack(string file)
    {
        using var zip = ZipFile.OpenRead(file);
        var entry = zip.GetEntry("modrinth.index.json") ?? throw new InvalidDataException("Modrinth 모드팩(.mrpack) 파일이 아니에요.");
        JsonNode index;
        using (var s = entry.Open()) index = JsonNode.Parse(s)!;
        var deps = index["dependencies"]!.AsObject();
        var loader = deps.ContainsKey("fabric-loader") ? "fabric"
            : deps.ContainsKey("quilt-loader") ? "quilt"
            : deps.ContainsKey("neoforge") ? "neoforge"
            : deps.ContainsKey("forge") ? "forge"
            : "vanilla";
        var files = index["files"]!.AsArray();
        return new ImportSource
        {
            Name = (string?)index["name"] ?? Path.GetFileNameWithoutExtension(file),
            Launcher = "모드팩",
            GameDir = "",
            Version = (string?)deps["minecraft"],
            Loader = loader,
            ModCount = files.Count(f => ((string?)f!["path"] ?? "").StartsWith("mods/", StringComparison.Ordinal)),
            PackFile = file,
        };
    }

    private static IEnumerable<string> ModFiles(string gameDir)
    {
        var mods = Path.Combine(gameDir, "mods");
        if (!Directory.Exists(mods)) return Array.Empty<string>();
        return Directory.EnumerateFiles(mods)
            .Where(f => f.EndsWith(".jar", StringComparison.OrdinalIgnoreCase) || f.EndsWith(".jar.disabled", StringComparison.OrdinalIgnoreCase))
            .Where(f => !Path.GetFileName(f).StartsWith("opticlient-hud-", StringComparison.OrdinalIgnoreCase));
    }

    private static int CountMods(string gameDir) => ModFiles(gameDir).Count();

    private static readonly Regex VersionInName = new(@"(?<![\d.])(26\.\d+(?:\.\d+)?|1\.\d+(?:\.\d+)?)(?![\d.])", RegexOptions.Compiled);

    /// <summary>메타데이터가 없는 폴더(공식 런처, Modrinth App): 모드 파일과 options.txt 로 버전·로더를 알아낸다</summary>
    private static void Detect(ImportSource s)
    {
        var jars = ModFiles(s.GameDir).ToList();
        s.ModCount = jars.Count;

        // 로더: 모드 파일 안의 설명 파일 종류로 판단 (최대 12개만 열어 봄)
        var loaderVotes = new Dictionary<string, int>();
        var versionVotes = new Dictionary<string, int>();
        foreach (var jar in jars.Take(12))
        {
            var info = ReadModInfo(jar);
            if (info.Loader != null) loaderVotes[info.Loader] = loaderVotes.GetValueOrDefault(info.Loader) + 1;
            if (info.Minecraft != null) versionVotes[info.Minecraft] = versionVotes.GetValueOrDefault(info.Minecraft) + 2;
        }
        // 버전: 파일 이름의 버전 표기 (sodium-fabric-0.6.13+mc1.21.1.jar 등) 중 OptiClient 가 아는 버전만 센다
        var known = GameTarget.All.Select(t => t.Version).ToHashSet();
        foreach (var jar in jars)
        foreach (Match m in VersionInName.Matches(Path.GetFileName(jar)))
            if (known.Contains(m.Value)) versionVotes[m.Value] = versionVotes.GetValueOrDefault(m.Value) + 1;

        s.Loader = jars.Count == 0 ? "vanilla" : loaderVotes.Count == 0 ? null : loaderVotes.MaxBy(kv => kv.Value).Key;

        string? fromOptions = null;
        if (s.OptionsFile != null)
        {
            var line = File.ReadLines(s.OptionsFile).FirstOrDefault(l => l.StartsWith("version:", StringComparison.Ordinal));
            if (line != null && int.TryParse(line[8..], out var dv))
                fromOptions = DataVersions.GetValueOrDefault(dv);
            else if (line == null && IsLegacyOptions(ReadOptions(s.OptionsFile, false)))
                fromOptions = "1.8.9";
        }
        s.Version = versionVotes.Count > 0 ? versionVotes.MaxBy(kv => kv.Value).Key : fromOptions;
    }

    private sealed record ModInfo(string? Loader, string? Id, string? Minecraft);

    /// <summary>모드 jar 안의 설명 파일을 읽어 로더·모드 id·필요한 마인크래프트 버전을 얻는다</summary>
    private static ModInfo ReadModInfo(string jar)
    {
        try
        {
            using var zip = ZipFile.OpenRead(jar);
            var fabric = zip.GetEntry("fabric.mod.json");
            if (fabric != null)
            {
                using var s = fabric.Open();
                using var r = new StreamReader(s);
                var j = JsonNode.Parse(r.ReadToEnd(), documentOptions: new JsonDocumentOptions { AllowTrailingCommas = true, CommentHandling = JsonCommentHandling.Skip });
                string? mcVer = null;
                var dep = j?["depends"]?["minecraft"];
                var depText = dep is JsonArray a ? string.Join(" ", a.Select(x => (string?)x)) : dep?.ToString();
                if (depText != null)
                {
                    var known = GameTarget.All.Select(t => t.Version).ToHashSet();
                    mcVer = VersionInName.Matches(depText).Select(m => m.Value).FirstOrDefault(known.Contains);
                }
                return new ModInfo("fabric", (string?)j?["id"], mcVer);
            }
            if (zip.GetEntry("quilt.mod.json") != null) return new ModInfo("quilt", null, null);
            if (zip.GetEntry("META-INF/neoforge.mods.toml") != null) return new ModInfo("neoforge", null, null);
            if (zip.GetEntry("META-INF/mods.toml") != null) return new ModInfo("forge", null, null);
            var mcmod = zip.GetEntry("mcmod.info");
            if (mcmod != null)
            {
                using var s = mcmod.Open();
                using var r = new StreamReader(s);
                var m = Regex.Match(r.ReadToEnd(), "\"modid\"\\s*:\\s*\"([^\"]+)\"");
                return new ModInfo("forge", m.Success ? m.Groups[1].Value : null, null);
            }
        }
        catch (Exception)
        {
            // 깨진 jar 는 건너뜀
        }
        return new ModInfo(null, null, null);
    }

    // =====================================================================
    // 가져오기
    // =====================================================================

    /// <summary>source 를 target 프로필로 가져온다. isNew 면 방금 만든 프로필 (성능 설정부터 깐다)</summary>
    public static async Task RunAsync(ImportSource source, Profile target, bool isNew, ImportOptions o, IProgress<string> log, CancellationToken ct)
    {
        Directory.CreateDirectory(target.GameDir);
        var sameGame = source.Target != null && source.Target.Version == target.GameVersion && source.Target.Loader == target.Loader && source.HasMods;
        if (source.PackFile != null) await ExtractPackAsync(source, includeMods: sameGame && o.Mods, log, ct);

        // 새 프로필(또는 한 번도 설치 안 한 프로필)은 OptiClient 성능 설정을 먼저 깔고 그 위에 가져온 설정을 얹는다
        if (isNew || !File.Exists(Path.Combine(target.GameDir, "options.txt")))
            Presets.ApplyPerformanceOptions(target, firstInstall: true);

        // 파일 복사는 오래 걸릴 수 있어서 (리소스팩 수 GB) 화면이 멈추지 않게 백그라운드에서 한다


        if (o.GameSettings && source.OptionsFile != null)
        {
            var n = await Task.Run(() => MergeOptions(source, target, o.Graphics, o.ResourcePacks), ct);
            log.Report($"게임 설정 {n}개를 가져왔어요{(o.Graphics ? "" : " (그래픽 설정은 OptiClient 최적화 값 유지)")}.");
        }
        if (o.ClientHud && source.Hud != null) await Task.Run(() => ClientProfiles.Apply(source.Hud, target.GameDir, log), ct);
        if (o.Servers) await Task.Run(() => CopyServers(source, target, log), ct);
        if (o.ResourcePacks)
        {
            log.Report("리소스팩 복사 중...");
            log.Report($"리소스팩 {await Task.Run(() => CopyEntries(source.GameDir, target.GameDir, "resourcepacks"), ct)}개를 복사했어요.");
        }
        if (o.ShaderPacks) log.Report($"셰이더팩 {await Task.Run(() => CopyEntries(source.GameDir, target.GameDir, "shaderpacks"), ct)}개를 복사했어요.");
        if (o.Worlds)
        {
            log.Report("월드 복사 중...");
            log.Report($"월드 {await Task.Run(() => CopyEntries(source.GameDir, target.GameDir, "saves"), ct)}개를 복사했어요.");
        }

        if (sameGame && o.Configs)
        {
            var n = await Task.Run(() => CopyTree(Path.Combine(source.GameDir, "config"), Path.Combine(target.GameDir, "config"),
                skip: f => Path.GetFileName(f).Equals("opticlient.json", StringComparison.OrdinalIgnoreCase)), ct);
            log.Report($"모드 설정 파일 {n}개를 복사했어요.");
        }
        if (sameGame && o.Mods) await ImportModsAsync(source, target, log, ct);
        else if (o.Mods && source.HasMods && source.ModCount > 0)
            log.Report($"(건너뜀) 모드: {source.Version ?? "?"} {source.LoaderLabel} 용이라 {target.Target.Label} 프로필에는 넣지 않았어요.");
    }

    // ---------------- 게임 설정 (options.txt) ----------------

    /// <summary>그래픽(성능) 관련 설정 이름. 기본으로는 OptiClient 최적화 값을 유지한다</summary>
    private static readonly HashSet<string> GraphicsKeys = new(StringComparer.Ordinal)
    {
        "renderDistance", "simulationDistance", "maxFps", "enableVsync", "graphicsMode", "fancyGraphics", "ao",
        "renderClouds", "particles", "entityShadows", "biomeBlendRadius", "mipmapLevels", "entityDistanceScaling",
        "graphicsPreset", "cutoutLeaves", "chunkSectionFadeInTime", "inactivityFpsLimit", "useVbo", "fboEnable",
        "prioritizeChunkUpdates", "fullscreen", "fullscreenResolution", "overrideWidth", "overrideHeight",
        "anaglyph3d", "useNativeTransport",
    };

    /// <summary>바뀌면 안 되는 값 (게임 데이터 버전 등)</summary>
    private static readonly HashSet<string> NeverCopy = new(StringComparer.Ordinal) { "version", "lastServer" };

    private static Dictionary<string, string> ReadOptions(string file, bool lunar)
    {
        var d = new Dictionary<string, string>(StringComparer.Ordinal);
        if (lunar)
        {
            var j = JsonNode.Parse(File.ReadAllText(file))!.AsObject();
            foreach (var (k, v) in j)
            {
                if (v == null) continue;
                d[k] = v is JsonValue jv && jv.TryGetValue<string>(out var s) ? s : v.ToJsonString();
            }
            // Lunar 는 시야각을 도(°)로 저장한다 → options.txt 형식(0 = 70°, 1 = 110°)
            if (d.TryGetValue("fov", out var fov) && double.TryParse(fov, System.Globalization.NumberStyles.Float, System.Globalization.CultureInfo.InvariantCulture, out var deg) && deg > 2)
                d["fov"] = ((deg - 70) / 40).ToString("0.###", System.Globalization.CultureInfo.InvariantCulture);
            return d;
        }
        foreach (var line in File.ReadLines(file))
        {
            var i = line.IndexOf(':');
            if (i > 0) d[line[..i]] = line[(i + 1)..];
        }
        return d;
    }

    /// <summary>키 값이 숫자면 1.8.9 형식</summary>
    private static bool IsLegacyOptions(Dictionary<string, string> o) =>
        o.TryGetValue("key_key.forward", out var v) && int.TryParse(v, out _);

    private static int MergeOptions(ImportSource source, Profile target, bool graphics, bool resourcePacks)
    {
        var src = ReadOptions(source.OptionsFile!, source.LunarOptions);
        var srcLegacy = IsLegacyOptions(src);
        var dstLegacy = target.GameVersion == "1.8.9";

        var file = Path.Combine(target.GameDir, "options.txt");
        var lines = File.Exists(file) ? File.ReadAllLines(file).ToList() : new List<string>();
        var count = 0;
        foreach (var (key, raw) in src)
        {
            if (NeverCopy.Contains(key)) continue;
            if (!graphics && GraphicsKeys.Contains(key)) continue;
            if (!resourcePacks && key is "resourcePacks" or "incompatibleResourcePacks") continue;
            var value = raw;
            if (key.StartsWith("key_", StringComparison.Ordinal) && srcLegacy != dstLegacy)
            {
                var converted = dstLegacy ? KeyNames.ToLegacy(value) : KeyNames.ToModern(value);
                if (converted == null) continue;
                value = converted;
            }
            if (key == "lang") value = dstLegacy ? LegacyLang(value) : value.ToLowerInvariant();
            if (key == "resourcePacks" && srcLegacy != dstLegacy) continue; // 팩 목록 형식이 달라서 버전이 다르면 옮기지 않음

            var idx = lines.FindIndex(l => l.StartsWith(key + ":", StringComparison.Ordinal));
            var line = $"{key}:{value}";
            if (idx >= 0) lines[idx] = line;
            else lines.Add(line);
            count++;
        }
        File.WriteAllLines(file, lines);
        return count;
    }

    /// <summary>ko_kr → ko_KR (1.8.9 형식)</summary>
    private static string LegacyLang(string v)
    {
        var i = v.IndexOf('_');
        return i > 0 ? v[..i].ToLowerInvariant() + "_" + v[(i + 1)..].ToUpperInvariant() : v;
    }

    // ---------------- 파일 복사 ----------------

    private static void CopyServers(ImportSource source, Profile target, IProgress<string> log)
    {
        var src = Path.Combine(source.GameDir, "servers.dat");
        if (!File.Exists(src)) return;
        var dst = Path.Combine(target.GameDir, "servers.dat");
        if (File.Exists(dst)) File.Copy(dst, dst + ".opticlient-backup", true);
        File.Copy(src, dst, true);
        log.Report("서버 목록을 가져왔어요." + (File.Exists(dst + ".opticlient-backup") ? " (원래 목록은 servers.dat.opticlient-backup 으로 보관)" : ""));
    }

    /// <summary>폴더 안의 파일·폴더를 복사한다 (같은 이름이 이미 있으면 건너뜀). 복사한 개수</summary>
    private static int CopyEntries(string srcGame, string dstGame, string folder)
    {
        var src = Path.Combine(srcGame, folder);
        if (!Directory.Exists(src)) return 0;
        var dst = Path.Combine(dstGame, folder);
        Directory.CreateDirectory(dst);
        var n = 0;
        foreach (var f in Directory.GetFiles(src))
        {
            var to = Path.Combine(dst, Path.GetFileName(f));
            if (File.Exists(to)) continue;
            File.Copy(f, to);
            n++;
        }
        foreach (var d in Directory.GetDirectories(src))
        {
            var to = Path.Combine(dst, Path.GetFileName(d));
            if (Directory.Exists(to)) continue;
            CopyTree(d, to);
            n++;
        }
        return n;
    }

    /// <summary>폴더를 통째로 복사 (같은 이름 파일은 덮어씀). 복사한 파일 개수</summary>
    private static int CopyTree(string src, string dst, Func<string, bool>? skip = null)
    {
        if (!Directory.Exists(src)) return 0;
        var n = 0;
        foreach (var f in Directory.EnumerateFiles(src, "*", SearchOption.AllDirectories))
        {
            if (skip != null && skip(f)) continue;
            var to = Path.Combine(dst, Path.GetRelativePath(src, f));
            Directory.CreateDirectory(Path.GetDirectoryName(to)!);
            File.Copy(f, to, true);
            n++;
        }
        return n;
    }

    // ---------------- 모드 ----------------

    private static string Norm(string s) => new(s.ToLowerInvariant().Where(char.IsLetterOrDigit).ToArray());

    private static async Task ImportModsAsync(ImportSource source, Profile target, IProgress<string> log, CancellationToken ct)
    {
        var jars = ModFiles(source.GameDir).ToList();
        if (jars.Count == 0) return;
        log.Report($"모드 {jars.Count}개를 확인하는 중...");
        Directory.CreateDirectory(target.ModsDir);

        // 이미 프로필에 있는 모드 (Modrinth 프로젝트 id · 이름 · jar 안의 모드 id)
        var haveIds = new HashSet<string>(StringComparer.OrdinalIgnoreCase);
        var haveNames = new HashSet<string>();
        foreach (var m in target.Mods)
        {
            haveIds.Add(m.ProjectId);
            haveNames.Add(Norm(m.ProjectId));
            haveNames.Add(Norm(m.Title));
            if (m.Source == ModSource.Modrinth && m.ProjectId.Any(c => c == '-' || char.IsLower(c)))
            {
                try
                {
                    if (await Modrinth.GetProjectIdAsync(m.ProjectId, ct) is { } id) haveIds.Add(id);
                }
                catch (HttpRequestException) { }
            }
            if (m.FileName != null)
            {
                var f = Path.Combine(target.ModsDir, m.FileName);
                if (!File.Exists(f)) f += ".disabled";
                if (File.Exists(f) && ReadModInfo(f).Id is { } modId) haveNames.Add(Norm(modId));
            }
        }

        // Modrinth 에 있는 모드인지 파일 해시로 한 번에 찾는다 (있으면 나중에 업데이트할 수 있게 Modrinth 모드로 등록)
        var hashes = await Task.Run(() => jars.ToDictionary(j => j, Sha1), ct);
        var found = new Dictionary<string, (string ProjectId, string Title)>();
        try
        {
            found = await LookupAsync(hashes.Values.Distinct().ToList(), ct);
        }
        catch (HttpRequestException ex)
        {
            log.Report($"(안내) Modrinth 에 연결하지 못해 모두 로컬 파일로 넣어요: {ex.Message}");
        }

        int added = 0, skipped = 0;
        foreach (var jar in jars)
        {
            ct.ThrowIfCancellationRequested();
            var disabled = jar.EndsWith(".disabled", StringComparison.OrdinalIgnoreCase);
            var fileName = disabled ? Path.GetFileName(jar)[..^".disabled".Length] : Path.GetFileName(jar);
            var info = ReadModInfo(jar);
            found.TryGetValue(hashes[jar], out var hit);

            var dup = (hit.ProjectId != null && haveIds.Contains(hit.ProjectId))
                      || (info.Id != null && haveNames.Contains(Norm(info.Id)))
                      || (hit.Title != null && haveNames.Contains(Norm(hit.Title)));
            if (dup)
            {
                skipped++;
                continue;
            }

            File.Copy(jar, Path.Combine(target.ModsDir, fileName), true);
            var entry = hit.ProjectId != null
                ? new ModEntry { ProjectId = hit.ProjectId, Title = hit.Title ?? fileName, Source = ModSource.Modrinth, FileName = fileName }
                : new ModEntry { ProjectId = fileName, Title = Path.GetFileNameWithoutExtension(fileName), Source = ModSource.Local, FileName = fileName };
            entry.Enabled = !disabled;
            target.Mods.Add(entry);
            Installer.ApplyEnabled(target, entry);
            if (hit.ProjectId != null) haveIds.Add(hit.ProjectId);
            if (info.Id != null) haveNames.Add(Norm(info.Id));
            added++;
        }
        log.Report($"모드 {added}개를 넣었어요." + (skipped > 0 ? $" (이미 있는 모드 {skipped}개는 건너뜀)" : ""));
    }

    private static string Sha1(string file)
    {
        using var fs = File.OpenRead(file);
        return Convert.ToHexString(SHA1.HashData(fs)).ToLowerInvariant();
    }

    /// <summary>sha1 해시 → (프로젝트 id, 이름). Modrinth 에 없는 파일은 빠진다</summary>
    private static async Task<Dictionary<string, (string ProjectId, string Title)>> LookupAsync(List<string> hashes, CancellationToken ct)
    {
        var result = new Dictionary<string, (string, string)>();
        var projectOf = new Dictionary<string, string>();
        foreach (var chunk in hashes.Chunk(300))
        {
            var body = new JsonObject { ["hashes"] = new JsonArray(chunk.Select(h => (JsonNode)h).ToArray()), ["algorithm"] = "sha1" };
            using var resp = await Modrinth.Http.PostAsync("https://api.modrinth.com/v2/version_files",
                new StringContent(body.ToJsonString(), Encoding.UTF8, "application/json"), ct);
            resp.EnsureSuccessStatusCode();
            var obj = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!.AsObject();
            foreach (var (hash, v) in obj)
                if ((string?)v?["project_id"] is { } pid) projectOf[hash] = pid;
        }
        var titles = new Dictionary<string, string>();
        foreach (var chunk in projectOf.Values.Distinct().Chunk(100))
        {
            var ids = Uri.EscapeDataString(new JsonArray(chunk.Select(i => (JsonNode)i).ToArray()).ToJsonString());
            var arr = JsonNode.Parse(await Modrinth.Http.GetStringAsync($"https://api.modrinth.com/v2/projects?ids={ids}", ct))!.AsArray();
            foreach (var p in arr) titles[(string)p!["id"]!] = (string?)p["title"] ?? (string)p["id"]!;
        }
        foreach (var (hash, pid) in projectOf) result[hash] = (pid, titles.GetValueOrDefault(pid, pid));
        return result;
    }

    // ---------------- .mrpack ----------------

    /// <summary>모드팩이 내려받을 수 있는 주소 (Modrinth 모드팩 규격이 허용하는 곳만)</summary>
    private static readonly string[] AllowedHosts = { "cdn.modrinth.com", "github.com", "raw.githubusercontent.com", "gitlab.com" };

    /// <summary>.mrpack 을 임시 폴더에 풀고 그 안의 파일을 내려받는다 (그 폴더를 가져오기 원본으로 씀)</summary>
    private static async Task ExtractPackAsync(ImportSource s, bool includeMods, IProgress<string> log, CancellationToken ct)
    {
        var dir = Path.Combine(Paths.CacheDir, "import", Guid.NewGuid().ToString("N")[..8]);
        Directory.CreateDirectory(dir);
        var root = Path.GetFullPath(dir) + Path.DirectorySeparatorChar;
        string Safe(string rel)
        {
            var full = Path.GetFullPath(Path.Combine(dir, rel));
            if (!full.StartsWith(root, StringComparison.OrdinalIgnoreCase)) throw new InvalidDataException($"잘못된 경로가 들어 있어요: {rel}");
            return full;
        }

        using var zip = ZipFile.OpenRead(s.PackFile!);
        JsonNode index;
        using (var st = zip.GetEntry("modrinth.index.json")!.Open()) index = JsonNode.Parse(st)!;

        // overrides → client-overrides 순서로 풀기 (뒤가 앞을 덮음)
        foreach (var prefix in new[] { "overrides/", "client-overrides/" })
        foreach (var e in zip.Entries.Where(e => e.FullName.StartsWith(prefix, StringComparison.Ordinal) && !e.FullName.EndsWith('/')))
        {
            var to = Safe(e.FullName[prefix.Length..]);
            Directory.CreateDirectory(Path.GetDirectoryName(to)!);
            e.ExtractToFile(to, true);
        }

        var files = index["files"]!.AsArray();
        var i = 0;
        foreach (var f in files)
        {
            ct.ThrowIfCancellationRequested();
            i++;
            if ((string?)f!["env"]?["client"] == "unsupported") continue;
            var path = (string)f["path"]!;
            if (!includeMods && path.StartsWith("mods/", StringComparison.Ordinal)) continue; // 넣지 않을 모드는 받지 않음
            var url = f["downloads"]!.AsArray().Select(u => (string?)u).FirstOrDefault(u =>
                u != null && Uri.TryCreate(u, UriKind.Absolute, out var uri) && uri.Scheme == "https" && AllowedHosts.Contains(uri.Host));
            if (url == null)
            {
                log.Report($"(건너뜀) {path}: 허용되지 않은 주소");
                continue;
            }
            log.Report($"모드팩 파일 받는 중 ({i}/{files.Count}) {Path.GetFileName(path)}");
            var to = Safe(path);
            await Modrinth.DownloadAsync(url, to, ct);
            var want = (string?)f["hashes"]?["sha1"];
            if (want != null && !string.Equals(Sha1(to), want, StringComparison.OrdinalIgnoreCase))
            {
                File.Delete(to);
                throw new InvalidDataException($"{path} 파일이 손상됐어요 (해시가 달라요).");
            }
        }
        s.GameDir = dir;
        s.OptionsFile = ExistingFile(Path.Combine(dir, "options.txt"));
    }
}

/// <summary>1.8.9(LWJGL2 키 번호) ↔ 1.13+(key.keyboard.이름) 키 배치 변환</summary>
public static class KeyNames
{
    private static readonly (int Code, string Name)[] Table =
    {
        (0, "key.keyboard.unknown"), (1, "key.keyboard.escape"),
        (2, "key.keyboard.1"), (3, "key.keyboard.2"), (4, "key.keyboard.3"), (5, "key.keyboard.4"), (6, "key.keyboard.5"),
        (7, "key.keyboard.6"), (8, "key.keyboard.7"), (9, "key.keyboard.8"), (10, "key.keyboard.9"), (11, "key.keyboard.0"),
        (12, "key.keyboard.minus"), (13, "key.keyboard.equal"), (14, "key.keyboard.backspace"), (15, "key.keyboard.tab"),
        (16, "key.keyboard.q"), (17, "key.keyboard.w"), (18, "key.keyboard.e"), (19, "key.keyboard.r"), (20, "key.keyboard.t"),
        (21, "key.keyboard.y"), (22, "key.keyboard.u"), (23, "key.keyboard.i"), (24, "key.keyboard.o"), (25, "key.keyboard.p"),
        (26, "key.keyboard.left.bracket"), (27, "key.keyboard.right.bracket"), (28, "key.keyboard.enter"), (29, "key.keyboard.left.control"),
        (30, "key.keyboard.a"), (31, "key.keyboard.s"), (32, "key.keyboard.d"), (33, "key.keyboard.f"), (34, "key.keyboard.g"),
        (35, "key.keyboard.h"), (36, "key.keyboard.j"), (37, "key.keyboard.k"), (38, "key.keyboard.l"), (39, "key.keyboard.semicolon"),
        (40, "key.keyboard.apostrophe"), (41, "key.keyboard.grave.accent"), (42, "key.keyboard.left.shift"), (43, "key.keyboard.backslash"),
        (44, "key.keyboard.z"), (45, "key.keyboard.x"), (46, "key.keyboard.c"), (47, "key.keyboard.v"), (48, "key.keyboard.b"),
        (49, "key.keyboard.n"), (50, "key.keyboard.m"), (51, "key.keyboard.comma"), (52, "key.keyboard.period"), (53, "key.keyboard.slash"),
        (54, "key.keyboard.right.shift"), (55, "key.keyboard.keypad.multiply"), (56, "key.keyboard.left.alt"), (57, "key.keyboard.space"),
        (58, "key.keyboard.caps.lock"),
        (59, "key.keyboard.f1"), (60, "key.keyboard.f2"), (61, "key.keyboard.f3"), (62, "key.keyboard.f4"), (63, "key.keyboard.f5"),
        (64, "key.keyboard.f6"), (65, "key.keyboard.f7"), (66, "key.keyboard.f8"), (67, "key.keyboard.f9"), (68, "key.keyboard.f10"),
        (69, "key.keyboard.num.lock"), (70, "key.keyboard.scroll.lock"),
        (71, "key.keyboard.keypad.7"), (72, "key.keyboard.keypad.8"), (73, "key.keyboard.keypad.9"), (74, "key.keyboard.keypad.subtract"),
        (75, "key.keyboard.keypad.4"), (76, "key.keyboard.keypad.5"), (77, "key.keyboard.keypad.6"), (78, "key.keyboard.keypad.add"),
        (79, "key.keyboard.keypad.1"), (80, "key.keyboard.keypad.2"), (81, "key.keyboard.keypad.3"), (82, "key.keyboard.keypad.0"),
        (83, "key.keyboard.keypad.decimal"), (87, "key.keyboard.f11"), (88, "key.keyboard.f12"),
        (156, "key.keyboard.keypad.enter"), (157, "key.keyboard.right.control"), (181, "key.keyboard.keypad.divide"),
        (184, "key.keyboard.right.alt"), (197, "key.keyboard.pause"), (199, "key.keyboard.home"), (200, "key.keyboard.up"),
        (201, "key.keyboard.page.up"), (203, "key.keyboard.left"), (205, "key.keyboard.right"), (207, "key.keyboard.end"),
        (208, "key.keyboard.down"), (209, "key.keyboard.page.down"), (210, "key.keyboard.insert"), (211, "key.keyboard.delete"),
        // 마우스: 1.8.9 는 버튼 번호 - 100
        (-100, "key.mouse.left"), (-99, "key.mouse.right"), (-98, "key.mouse.middle"), (-97, "key.mouse.4"), (-96, "key.mouse.5"),
    };

    private static readonly Dictionary<int, string> ByCode = Table.ToDictionary(t => t.Code, t => t.Name);
    private static readonly Dictionary<string, int> ByName = Table.ToDictionary(t => t.Name, t => t.Code);

    public static string? ToModern(string legacy) =>
        int.TryParse(legacy, out var code) && ByCode.TryGetValue(code, out var name) ? name : null;

    public static string? ToLegacy(string modern) =>
        ByName.TryGetValue(modern, out var code) ? code.ToString() : null;
}
