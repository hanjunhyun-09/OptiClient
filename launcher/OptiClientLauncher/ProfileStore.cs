using System.Collections.ObjectModel;
using System.IO;
using System.Text.Json;
using System.Text.Json.Serialization;

namespace OptiClientLauncher;

public static class Paths
{
    public static string AppData => Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);

    /// <summary>공식 런처가 쓰는 .minecraft 폴더 (버전/라이브러리 공유)</summary>
    public static string MinecraftDir =>
        Environment.GetEnvironmentVariable("OPTICLIENT_MINECRAFT_DIR") is { Length: > 0 } d ? d : Path.Combine(AppData, ".minecraft");

    /// <summary>OptiClient 데이터 폴더 (프로필별 게임 폴더)</summary>
    public static string Root =>
        Environment.GetEnvironmentVariable("OPTICLIENT_HOME") is { Length: > 0 } d ? d : Path.Combine(AppData, ".opticlient");

    public static string ProfilesDir => Path.Combine(Root, "profiles");
    public static string ProfilesFile => Path.Combine(Root, "profiles.json");
    public static string CacheDir => Path.Combine(Root, "cache");

    /// <summary>런처 exe 옆의 hud 폴더 (빌드한 HUD 모드 jar)</summary>
    public static string BundledHudDir => Path.Combine(AppContext.BaseDirectory, "hud");
}

public static class ProfileStore
{
    private static readonly JsonSerializerOptions Options = new()
    {
        WriteIndented = true,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
        Converters = { new JsonStringEnumConverter() },
    };

    public static ObservableCollection<Profile> Load()
    {
        try
        {
            if (File.Exists(Paths.ProfilesFile))
            {
                var list = JsonSerializer.Deserialize<List<Profile>>(File.ReadAllText(Paths.ProfilesFile), Options);
                if (list != null && list.Count > 0) return new ObservableCollection<Profile>(list);
            }
        }
        catch (Exception)
        {
            // 손상된 파일은 백업해 두고 새로 시작
            File.Copy(Paths.ProfilesFile, Paths.ProfilesFile + ".broken", true);
        }

        return new ObservableCollection<Profile>(Presets.DefaultProfiles());
    }

    public static void Save(IEnumerable<Profile> profiles)
    {
        Directory.CreateDirectory(Paths.Root);
        var tmp = Paths.ProfilesFile + ".tmp";
        File.WriteAllText(tmp, JsonSerializer.Serialize(profiles.ToList(), Options));
        File.Move(tmp, Paths.ProfilesFile, true);
    }
}

public static class Json
{
    /// <summary>들여쓰기 + 한글을 그대로 쓰는 JSON 설정</summary>
    public static readonly JsonSerializerOptions Pretty = new()
    {
        WriteIndented = true,
        Encoder = System.Text.Encodings.Web.JavaScriptEncoder.UnsafeRelaxedJsonEscaping,
    };
}
