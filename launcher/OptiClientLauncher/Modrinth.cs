using System.IO;
using System.Net.Http;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

public sealed class ModrinthHit : Observable
{
    private bool _picked;

    public required string ProjectId { get; init; }
    public required string Slug { get; init; }
    public required string Title { get; init; }
    public string Description { get; init; } = "";
    public long Downloads { get; init; }
    public string? IconUrl { get; init; }
    public string Author { get; init; } = "";
    public string Categories { get; init; } = "";
    public bool AlreadyInProfile { get; init; }

    /// <summary>"담기" 를 눌러 한꺼번에 추가할 목록에 들어갔는지</summary>
    public bool Picked { get => _picked; set { Set(ref _picked, value); Raise(nameof(PickLabel)); } }

    public string PickLabel => AlreadyInProfile ? "추가됨" : Picked ? "✓ 담김" : "＋ 담기";
    public string DownloadsLabel => Downloads >= 1_000_000 ? $"{Downloads / 1_000_000.0:0.#}M" : $"{Downloads / 1000.0:0.#}K";
    public string Initial => Title.Length > 0 ? Title[..1].ToUpperInvariant() : "?";
}

public sealed record ModrinthFile(string Url, string FileName, string ProjectId, List<string> RequiredDependencies);

/// <summary>Modrinth API v2 (https://docs.modrinth.com)</summary>
public static class Modrinth
{
    private const string Api = "https://api.modrinth.com/v2";

    public static readonly HttpClient Http = CreateClient();

    private static HttpClient CreateClient()
    {
        var c = new HttpClient { Timeout = TimeSpan.FromMinutes(5) };
        c.DefaultRequestHeaders.UserAgent.ParseAdd("OptiClientLauncher/1.0");
        return c;
    }

    private static string LoaderName(LoaderType l) => l == LoaderType.Fabric ? "fabric" : "forge";

    public static async Task<(List<ModrinthHit> Hits, int Total)> SearchAsync(string query, GameTarget target, int offset,
        string? category, ISet<string> installed, CancellationToken ct = default)
    {
        var facets = $"[[\"project_type:mod\"],[\"categories:{LoaderName(target.Loader)}\"],[\"versions:{target.Version}\"]"
                     + (string.IsNullOrEmpty(category) ? "" : $",[\"categories:{category}\"]") + "]";
        var index = string.IsNullOrWhiteSpace(query) ? "downloads" : "relevance";
        var url = $"{Api}/search?limit=30&offset={offset}&index={index}&query={Uri.EscapeDataString(query)}&facets={Uri.EscapeDataString(facets)}";
        var json = JsonNode.Parse(await Http.GetStringAsync(url, ct))!;
        var hits = json["hits"]!.AsArray().Select(h => new ModrinthHit
        {
            ProjectId = (string)h!["project_id"]!,
            Slug = (string)h["slug"]!,
            Title = (string)h["title"]!,
            Description = (string?)h["description"] ?? "",
            Downloads = (long)h["downloads"]!,
            IconUrl = string.IsNullOrEmpty((string?)h["icon_url"]) ? null : (string?)h["icon_url"],
            Author = (string?)h["author"] ?? "",
            Categories = string.Join(" · ", (h["display_categories"]?.AsArray() ?? new JsonArray()).Select(c => (string?)c).Take(3)),
            AlreadyInProfile = installed.Contains((string)h["project_id"]!) || installed.Contains((string)h["slug"]!),
        }).ToList();
        return (hits, (int?)json["total_hits"] ?? hits.Count);
    }

    public static async Task<string> GetTitleAsync(string projectId, CancellationToken ct = default)
    {
        var json = JsonNode.Parse(await Http.GetStringAsync($"{Api}/project/{Uri.EscapeDataString(projectId)}", ct))!;
        return (string?)json["title"] ?? projectId;
    }

    /// <summary>slug 또는 id 로 프로젝트 id 를 얻는다. 실패하면 null</summary>
    public static async Task<string?> GetProjectIdAsync(string projectIdOrSlug, CancellationToken ct = default)
    {
        using var resp = await Http.GetAsync($"{Api}/project/{Uri.EscapeDataString(projectIdOrSlug)}", ct);
        if (!resp.IsSuccessStatusCode) return null;
        return (string?)JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!["id"];
    }

    /// <summary>게임 버전·로더에 맞는 최신 파일을 찾는다. 없으면 null</summary>
    public static async Task<ModrinthFile?> ResolveAsync(string projectIdOrSlug, GameTarget target, CancellationToken ct = default)
    {
        var loaders = Uri.EscapeDataString($"[\"{LoaderName(target.Loader)}\"]");
        var versions = Uri.EscapeDataString($"[\"{target.Version}\"]");
        var url = $"{Api}/project/{Uri.EscapeDataString(projectIdOrSlug)}/version?loaders={loaders}&game_versions={versions}";
        using var resp = await Http.GetAsync(url, ct);
        if (!resp.IsSuccessStatusCode) return null;
        var arr = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!.AsArray();
        if (arr.Count == 0) return null;

        // 정식 릴리스를 우선, 없으면 가장 최신 버전
        var version = arr.FirstOrDefault(v => (string?)v!["version_type"] == "release") ?? arr[0]!;
        var files = version["files"]!.AsArray();
        var file = files.FirstOrDefault(f => (bool?)f!["primary"] == true) ?? files[0]!;
        var deps = version["dependencies"]!.AsArray()
            .Where(d => (string?)d!["dependency_type"] == "required" && d["project_id"] != null)
            .Select(d => (string)d!["project_id"]!)
            .ToList();
        return new ModrinthFile((string)file["url"]!, (string)file["filename"]!, (string)version["project_id"]!, deps);
    }

    public static async Task DownloadAsync(string url, string destination, CancellationToken ct = default)
    {
        Directory.CreateDirectory(Path.GetDirectoryName(destination)!);
        var tmp = destination + ".part";
        using (var resp = await Http.GetAsync(url, HttpCompletionOption.ResponseHeadersRead, ct))
        {
            resp.EnsureSuccessStatusCode();
            await using var fs = File.Create(tmp);
            await resp.Content.CopyToAsync(fs, ct);
        }
        File.Move(tmp, destination, true);
    }
}
