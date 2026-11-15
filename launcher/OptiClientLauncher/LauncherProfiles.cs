using System.IO;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

/// <summary>공식 런처의 launcher_profiles.json 에 프로필(설치 구성)을 추가/수정한다.</summary>
public static class LauncherProfiles
{
    private static string FilePath => Path.Combine(Paths.MinecraftDir, "launcher_profiles.json");

    // 클라이언트용 G1GC 설정 (공식 런처 기본값 기반 + 힙 크기)
    private const string GcArgs =
        "-XX:+UnlockExperimentalVMOptions -XX:+UseG1GC -XX:G1NewSizePercent=20 -XX:G1ReservePercent=20 " +
        "-XX:MaxGCPauseMillis=50 -XX:G1HeapRegionSize=32M";

    public static string Key(Profile p) => $"opticlient-{p.Id}";

    public static void Register(Profile p, string versionId)
    {
        JsonObject root;
        if (File.Exists(FilePath))
        {
            var backup = FilePath + ".opticlient-backup";
            if (!File.Exists(backup)) File.Copy(FilePath, backup);
            root = JsonNode.Parse(File.ReadAllText(FilePath))?.AsObject() ?? new JsonObject();
        }
        else
        {
            Directory.CreateDirectory(Paths.MinecraftDir);
            root = new JsonObject();
        }

        if (root["profiles"] is not JsonObject profiles)
        {
            profiles = new JsonObject();
            root["profiles"] = profiles;
        }

        var now = DateTime.UtcNow.ToString("yyyy-MM-ddTHH:mm:ss.fffZ");
        var existing = profiles[Key(p)] as JsonObject;
        var args = $"-Xmx{p.MemoryMb}M -Xms{Math.Min(p.MemoryMb, 1024)}M {GcArgs} {p.ExtraJvmArgs}".Trim();

        profiles[Key(p)] = new JsonObject
        {
            ["name"] = $"OptiClient · {p.Name}",
            ["type"] = "custom",
            ["icon"] = "Furnace",
            ["created"] = (string?)existing?["created"] ?? now,
            ["lastUsed"] = now, // 가장 최근 사용으로 표시해 런처에서 맨 위에 오게 한다
            ["lastVersionId"] = versionId,
            ["gameDir"] = p.GameDir,
            ["javaArgs"] = args,
        };

        var tmp = FilePath + ".tmp";
        File.WriteAllText(tmp, root.ToJsonString(Json.Pretty));
        File.Move(tmp, FilePath, true);
    }

    public static void Remove(Profile p)
    {
        if (!File.Exists(FilePath)) return;
        var root = JsonNode.Parse(File.ReadAllText(FilePath))?.AsObject();
        if (root?["profiles"] is not JsonObject profiles || !profiles.Remove(Key(p))) return;
        File.WriteAllText(FilePath, root.ToJsonString(Json.Pretty));
    }
}
