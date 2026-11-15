using System.IO;

namespace OptiClientLauncher;

/// <summary>버전별 최적화 모드 프리셋과 options.txt 성능 설정</summary>
public static class Presets
{
    /// <summary>
    /// 최적화 프리셋 버전. 모드 구성을 바꾸면 올린다.
    /// 기존 프로필은 다음 설치 때 빠진 최적화 모드를 한 번만 채워 넣는다 (그 뒤 직접 뺀 모드는 다시 넣지 않음).
    /// </summary>
    public const int Version = 2;

    private static readonly (string Id, string Title)[] FabricOptimization =
    {
        ("fabric-api", "Fabric API"),
        ("sodium", "Sodium (렌더링 최적화)"),
        ("lithium", "Lithium (게임 로직 최적화)"),
        ("ferrite-core", "FerriteCore (메모리 절약)"),
        ("immediatelyfast", "ImmediatelyFast (UI/글자 렌더링)"),
        ("entityculling", "Entity Culling (안 보이는 엔티티 생략)"),
        ("modernfix", "ModernFix (로딩/메모리)"),
        ("dynamic-fps", "Dynamic FPS (창 비활성 시 FPS 절약)"),
        ("krypton", "Krypton (네트워크 최적화)"),
        ("moreculling", "MoreCulling (안 보이는 블록 면 생략)"),
        ("badoptimizations", "BadOptimizations (조명·화면 계산 줄이기)"),
        ("exordium", "Exordium (HUD·화면 UI 를 낮은 주기로 그려 FPS 향상)"),
        ("ixeris", "Ixeris (창·입력 처리를 별도 스레드로)"),
    };

    private static readonly (string Id, string Title)[] Forge189Optimization =
    {
        ("patcher", "PolyPatcher (1.8.9 최적화/버그 수정)"),
        ("entityculling", "Entity Culling (안 보이는 엔티티 생략)"),
    };

    /// <summary>해당 버전용 파일이 Modrinth 에 없는 모드 (프리셋에서 뺀다)</summary>
    private static readonly Dictionary<string, string[]> Unsupported = new()
    {
        ["1.21.11"] = new[] { "modernfix" },
        ["26.3"] = new[] { "modernfix", "exordium" },
    };

    /// <summary>Modrinth 정보에 빠져 있지만 실제로는 필요한 모드 (함께 설치)</summary>
    private static readonly Dictionary<string, (string Id, string Title)[]> Extra = new()
    {
        // MoreCulling 1.20.1 은 Cloth Config 가 없으면 게임이 시작되지 않는다
        ["1.20.1"] = new[] { ("cloth-config", "Cloth Config (MoreCulling 설정에 필요)") },
    };

    public static IEnumerable<ModEntry> OptimizationMods(GameTarget t)
    {
        var list = t.Loader == LoaderType.Fabric ? FabricOptimization : Forge189Optimization;
        var skip = Unsupported.TryGetValue(t.Version, out var s) ? s : Array.Empty<string>();
        var extra = t.Loader == LoaderType.Fabric && Extra.TryGetValue(t.Version, out var e) ? e : Array.Empty<(string Id, string Title)>();
        return list.Where(m => !skip.Contains(m.Id)).Concat(extra)
            .Select(m => new ModEntry { ProjectId = m.Id, Title = m.Title, Source = ModSource.Modrinth });
    }

    public static ModEntry HudMod() => new()
    {
        ProjectId = "opticlient-hud",
        Title = "OptiClient HUD (FPS·좌표·CPS·키 입력 등)",
        Source = ModSource.Bundled,
    };

    public static Profile NewProfile(string name, string version)
    {
        var p = new Profile { Name = name, GameVersion = version, PresetVersion = Version };
        p.Mods.Add(HudMod());
        foreach (var m in OptimizationMods(p.Target)) p.Mods.Add(m);
        if (p.Target.Version == "1.8.9") p.MemoryMb = 2048;
        return p;
    }

    public static IEnumerable<Profile> DefaultProfiles()
    {
        yield return NewProfile("최신 버전 (26.3)", "26.3");
        yield return NewProfile("1.21 최신 (1.21.11)", "1.21.11");
        yield return NewProfile("야생 (1.21.1)", "1.21.1");
        yield return NewProfile("모드팩 (1.20.1)", "1.20.1");
        yield return NewProfile("PvP (1.8.9)", "1.8.9");
    }

    // ---------------- options.txt ----------------

    private static readonly Dictionary<string, string> ModernOptions = new()
    {
        ["renderDistance"] = "8",
        ["simulationDistance"] = "6",
        ["maxFps"] = "260",
        ["enableVsync"] = "false",
        ["graphicsMode"] = "0",
        ["ao"] = "false",
        ["renderClouds"] = "\"false\"",
        ["particles"] = "2",
        ["entityShadows"] = "false",
        ["biomeBlendRadius"] = "0",
        ["mipmapLevels"] = "0",
        ["entityDistanceScaling"] = "0.75",
        // 1.21.11 / 26.x 에서 이름이 바뀐 설정 (옛 버전은 모르는 키라 무시함)
        ["graphicsPreset"] = "\"fast\"",
        ["cutoutLeaves"] = "false",
        ["chunkSectionFadeInTime"] = "0.0",
        ["inactivityFpsLimit"] = "\"minimized\"",
    };

    private static readonly Dictionary<string, string> LegacyOptions = new()
    {
        ["renderDistance"] = "8",
        ["maxFps"] = "260",
        ["enableVsync"] = "false",
        ["fancyGraphics"] = "false",
        ["ao"] = "0",
        ["renderClouds"] = "false",
        ["particles"] = "2",
        ["entityShadows"] = "false",
        ["mipmapLevels"] = "0",
        ["useVbo"] = "true",
        ["fboEnable"] = "true",
    };

    /// <summary>options.txt 에 성능 설정을 합친다. 다른 설정(키 배치 등)은 그대로 둔다.</summary>
    public static void ApplyPerformanceOptions(Profile p, bool firstInstall)
    {
        var file = Path.Combine(p.GameDir, "options.txt");
        var legacy = p.Target.Version == "1.8.9";
        var lines = File.Exists(file) ? File.ReadAllLines(file).ToList() : new List<string>();
        var values = new Dictionary<string, string>(legacy ? LegacyOptions : ModernOptions);
        if (firstInstall && lines.Count == 0) values["lang"] = legacy ? "ko_KR" : "ko_kr";

        foreach (var (key, value) in values)
        {
            var idx = lines.FindIndex(l => l.StartsWith(key + ":", StringComparison.Ordinal));
            var line = $"{key}:{value}";
            if (idx >= 0) lines[idx] = line;
            else lines.Add(line);
        }

        Directory.CreateDirectory(p.GameDir);
        File.WriteAllLines(file, lines);
    }
}
