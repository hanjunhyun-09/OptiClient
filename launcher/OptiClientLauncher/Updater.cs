using System.Diagnostics;
using System.IO;
using System.IO.Compression;
using System.Net.Http;
using System.Reflection;
using System.Security.Cryptography;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

/// <summary>
/// 자동 업데이트: GitHub Releases 의 최신 릴리스(OptiClient.zip)를 확인하고,
/// 새 버전이면 런처 exe 와 hud 폴더를 바꾼 뒤 다시 켠다.
/// 실행 중인 exe 는 덮어쓸 수 없지만 이름은 바꿀 수 있어서, 지금 exe 를 .old 로 바꾸고 새 exe 를 넣는다.
/// </summary>
public static class Updater
{
    /// <summary>릴리스를 올리는 GitHub 저장소 (소유자/이름)</summary>
    public const string Repo = "hanjunhyun-09/OptiClient";
    private const string AssetName = "OptiClient.zip";

    public sealed record Release(Version Version, string Tag, string Url, string Notes, string? Sha256);

    public static Version Current =>
        Assembly.GetExecutingAssembly().GetName().Version is { } v ? new Version(v.Major, v.Minor, Math.Max(0, v.Build)) : new Version(0, 0, 0);

    public static string CurrentLabel => $"v{Current}";

    private static string AppDir => AppContext.BaseDirectory;

    /// <summary>개발 중인 빌드(프로젝트 폴더 안 dist·bin)는 업데이트하지 않는다</summary>
    public static bool IsDevBuild
    {
        get
        {
            var dir = new DirectoryInfo(AppDir);
            for (var i = 0; i < 6 && dir != null; i++, dir = dir.Parent)
                if (File.Exists(Path.Combine(dir.FullName, "build-all.ps1"))) return true;
            return false;
        }
    }

    public static bool Enabled => Repo.Length > 0 && !IsDevBuild;

    /// <summary>지난번 업데이트 때 남은 파일 정리 (시작할 때 부름)</summary>
    public static void CleanUp()
    {
        try
        {
            foreach (var f in Directory.GetFiles(AppDir, "*.old")) File.Delete(f);
            var tmp = Path.Combine(Path.GetTempPath(), "opticlient-update");
            if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
        }
        catch (Exception)
        {
            // 아직 잠겨 있으면 다음에 지움
        }
    }

    /// <summary>최신 릴리스가 지금보다 새 버전이면 돌려준다 (없거나 확인 실패면 null)</summary>
    public static async Task<Release?> CheckAsync(CancellationToken ct = default)
    {
        if (Repo.Length == 0) return null;
        using var req = new HttpRequestMessage(HttpMethod.Get, $"https://api.github.com/repos/{Repo}/releases/latest");
        req.Headers.Accept.ParseAdd("application/vnd.github+json");
        using var resp = await Modrinth.Http.SendAsync(req, ct);
        if (!resp.IsSuccessStatusCode) return null;
        var j = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!;
        var tag = (string?)j["tag_name"] ?? "";
        if (!Version.TryParse(tag.TrimStart('v', 'V'), out var v)) return null;
        v = new Version(v.Major, v.Minor, Math.Max(0, v.Build));
        if (v <= Current) return null;
        var asset = j["assets"]?.AsArray().FirstOrDefault(a => (string?)a!["name"] == AssetName);
        var url = (string?)asset?["browser_download_url"];
        if (url == null) return null;
        var digest = (string?)asset!["digest"]; // "sha256:..." (GitHub 가 계산한 값)
        var sha = digest != null && digest.StartsWith("sha256:") ? digest[7..] : null;
        return new Release(v, tag, url, (string?)j["body"] ?? "", sha);
    }

    /// <summary>새 버전을 받아 파일을 바꾸고, 새 런처를 켠다. 호출한 쪽은 바로 종료해야 한다</summary>
    public static async Task ApplyAsync(Release r, IProgress<string> log, CancellationToken ct = default)
    {
        var tmp = Path.Combine(Path.GetTempPath(), "opticlient-update");
        if (Directory.Exists(tmp)) Directory.Delete(tmp, true);
        Directory.CreateDirectory(tmp);
        var zip = Path.Combine(tmp, AssetName);

        log.Report($"{r.Tag} 내려받는 중...");
        await Modrinth.DownloadAsync(r.Url, zip, ct);
        if (r.Sha256 != null)
        {
            await using var fs = File.OpenRead(zip);
            var got = Convert.ToHexString(await SHA256.HashDataAsync(fs, ct));
            if (!got.Equals(r.Sha256, StringComparison.OrdinalIgnoreCase))
                throw new InvalidDataException("받은 파일이 손상됐어요 (해시가 달라요). 잠시 뒤 다시 해 주세요.");
        }

        var unpacked = Path.Combine(tmp, "files");
        ZipFile.ExtractToDirectory(zip, unpacked);
        // zip 안은 OptiClient/... 구조
        var src = Directory.Exists(Path.Combine(unpacked, "OptiClient")) ? Path.Combine(unpacked, "OptiClient") : unpacked;
        var newExe = Path.Combine(src, "OptiClientLauncher.exe");
        if (!File.Exists(newExe)) throw new InvalidDataException("업데이트 파일에 런처가 없어요.");

        log.Report("파일 바꾸는 중...");
        // 1) hud 폴더: 예전 jar 를 지우고 새 jar 로
        var hudSrc = Path.Combine(src, "hud");
        if (Directory.Exists(hudSrc))
        {
            var hud = Path.Combine(AppDir, "hud");
            Directory.CreateDirectory(hud);
            foreach (var f in Directory.GetFiles(hud, "opticlient-hud-*.jar")) File.Delete(f);
            foreach (var f in Directory.GetFiles(hudSrc)) File.Copy(f, Path.Combine(hud, Path.GetFileName(f)), true);
        }
        // 2) 그 밖의 파일 (사용법 등)
        foreach (var f in Directory.GetFiles(src))
        {
            if (Path.GetFileName(f).Equals("OptiClientLauncher.exe", StringComparison.OrdinalIgnoreCase)) continue;
            File.Copy(f, Path.Combine(AppDir, Path.GetFileName(f)), true);
        }
        // 3) 런처 exe: 지금 것은 이름만 바꿔 두고 새 것을 넣는다
        var exe = Environment.ProcessPath ?? Path.Combine(AppDir, "OptiClientLauncher.exe");
        var old = exe + ".old";
        if (File.Exists(old)) File.Delete(old);
        File.Move(exe, old);
        try
        {
            File.Copy(newExe, exe, true);
        }
        catch
        {
            File.Move(old, exe, true); // 실패하면 원래대로
            throw;
        }

        log.Report($"{r.Tag} 로 업데이트했어요. 다시 켜는 중...");
        Process.Start(new ProcessStartInfo(exe) { UseShellExecute = true, WorkingDirectory = AppDir });
    }
}
