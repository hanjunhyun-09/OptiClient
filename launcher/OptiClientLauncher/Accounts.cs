using System.IO;
using System.Net.Http;
using System.Net.Http.Json;
using System.Security.Cryptography;
using System.Text;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

/// <summary>게임 실행에 쓰는 계정 (실행할 때마다 만든다. 토큰은 파일에 저장하지 않음)</summary>
public sealed record Account(string Name, string Uuid, string AccessToken, string Xuid, bool IsOffline);

/// <summary>accounts.json 에 저장되는 계정 정보</summary>
public sealed class SavedAccount
{
    public string Type { get; set; } = "offline"; // offline | official | microsoft
    public string Name { get; set; } = "";
    public string Uuid { get; set; } = "";
    public string Xuid { get; set; } = "0";
    /// <summary>Microsoft 새로고침 토큰 (Windows DPAPI 로 현재 사용자만 풀 수 있게 암호화)</summary>
    public string? ProtectedRefreshToken { get; set; }

    public bool IsOffline => Type == "offline";
    /// <summary>정품: 로그인은 공식 마인크래프트 런처에 맡기고, 우리가 플레이 버튼만 대신 누른다</summary>
    public bool IsOfficial => Type == "official";
    public string Label => Type switch { "offline" => "오프라인", "official" => "정품 · 공식 런처", _ => "Microsoft" };
}

public sealed class LauncherSettings
{
    /// <summary>Azure 에 등록하고 Minecraft API 승인을 받은 앱의 클라이언트 ID</summary>
    public string MicrosoftClientId { get; set; } = "";
    public SavedAccount? Account { get; set; }
    /// <summary>정품(공식 런처)으로 실행하면 게임이 켜진 뒤 공식 런처를 닫는다 (공식 런처의 keepLauncherOpen 을 끔)</summary>
    public bool CloseOfficialLauncher { get; set; } = true;
}

public static class AccountStore
{
    private static string FilePath => Path.Combine(Paths.Root, "accounts.json");

    public static LauncherSettings Load()
    {
        try
        {
            if (File.Exists(FilePath))
                return JsonSerializer.Deserialize<LauncherSettings>(File.ReadAllText(FilePath)) ?? new LauncherSettings();
        }
        catch (JsonException)
        {
        }
        return new LauncherSettings();
    }

    public static void Save(LauncherSettings s)
    {
        Directory.CreateDirectory(Paths.Root);
        File.WriteAllText(FilePath, JsonSerializer.Serialize(s, Json.Pretty));
    }

    public static string Protect(string secret) =>
        Convert.ToBase64String(ProtectedData.Protect(Encoding.UTF8.GetBytes(secret), null, DataProtectionScope.CurrentUser));

    public static string Unprotect(string data) =>
        Encoding.UTF8.GetString(ProtectedData.Unprotect(Convert.FromBase64String(data), null, DataProtectionScope.CurrentUser));

    /// <summary>오프라인 UUID (Java 의 UUID.nameUUIDFromBytes("OfflinePlayer:이름") 과 같은 값)</summary>
    public static string OfflineUuid(string name)
    {
        var hash = MD5.HashData(Encoding.UTF8.GetBytes("OfflinePlayer:" + name));
        hash[6] = (byte)((hash[6] & 0x0F) | 0x30); // 버전 3
        hash[8] = (byte)((hash[8] & 0x3F) | 0x80); // IETF 변형
        return Convert.ToHexString(hash).ToLowerInvariant();
    }

    public static bool IsValidOfflineName(string name) =>
        System.Text.RegularExpressions.Regex.IsMatch(name, "^[A-Za-z0-9_]{3,16}$");
}

/// <summary>
/// Microsoft 계정 로그인 (기기 코드 방식) → Xbox Live → XSTS → Minecraft.
/// Azure 에 앱을 등록하고 Mojang 의 Minecraft API 승인을 받은 클라이언트 ID 가 있어야 동작한다.
/// </summary>
public static class MicrosoftAuth
{
    private const string Authority = "https://login.microsoftonline.com/consumers/oauth2/v2.0";
    private const string Scope = "XboxLive.signin offline_access";
    private static HttpClient Http => Modrinth.Http;

    public sealed record DeviceCode(string UserCode, string VerificationUri, string Code, int Interval, int ExpiresIn);

    public sealed class AuthException(string message) : Exception(message);

    public static async Task<DeviceCode> StartAsync(string clientId, CancellationToken ct)
    {
        var resp = await Http.PostAsync($"{Authority}/devicecode", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["client_id"] = clientId,
            ["scope"] = Scope,
        }), ct);
        var json = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!;
        if (!resp.IsSuccessStatusCode)
            throw new AuthException($"로그인을 시작하지 못했습니다: {(string?)json["error_description"] ?? resp.StatusCode.ToString()}");
        return new DeviceCode((string)json["user_code"]!, (string)json["verification_uri"]!, (string)json["device_code"]!,
            (int?)json["interval"] ?? 5, (int?)json["expires_in"] ?? 900);
    }

    /// <summary>사용자가 브라우저에서 코드를 넣을 때까지 기다린다 → (Microsoft 액세스 토큰, 새로고침 토큰)</summary>
    public static async Task<(string Access, string Refresh)> WaitForLoginAsync(string clientId, DeviceCode code, CancellationToken ct)
    {
        var deadline = DateTime.UtcNow.AddSeconds(code.ExpiresIn);
        var interval = code.Interval;
        while (DateTime.UtcNow < deadline)
        {
            await Task.Delay(TimeSpan.FromSeconds(interval), ct);
            var resp = await Http.PostAsync($"{Authority}/token", new FormUrlEncodedContent(new Dictionary<string, string>
            {
                ["grant_type"] = "urn:ietf:params:oauth:grant-type:device_code",
                ["client_id"] = clientId,
                ["device_code"] = code.Code,
            }), ct);
            var json = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!;
            if (resp.IsSuccessStatusCode) return ((string)json["access_token"]!, (string)json["refresh_token"]!);
            switch ((string?)json["error"])
            {
                case "authorization_pending":
                    continue;
                case "slow_down":
                    interval += 5;
                    continue;
                case "authorization_declined":
                    throw new AuthException("로그인을 거절했습니다.");
                case "expired_token":
                    throw new AuthException("코드 유효 시간이 지났습니다. 다시 시도하세요.");
                default:
                    throw new AuthException($"로그인 실패: {(string?)json["error_description"] ?? (string?)json["error"]}");
            }
        }
        throw new AuthException("코드 유효 시간이 지났습니다. 다시 시도하세요.");
    }

    public static async Task<(string Access, string Refresh)> RefreshAsync(string clientId, string refreshToken, CancellationToken ct)
    {
        var resp = await Http.PostAsync($"{Authority}/token", new FormUrlEncodedContent(new Dictionary<string, string>
        {
            ["grant_type"] = "refresh_token",
            ["client_id"] = clientId,
            ["refresh_token"] = refreshToken,
            ["scope"] = Scope,
        }), ct);
        var json = JsonNode.Parse(await resp.Content.ReadAsStringAsync(ct))!;
        if (!resp.IsSuccessStatusCode) throw new AuthException("Microsoft 로그인이 만료됐습니다. 계정 화면에서 다시 로그인하세요.");
        return ((string)json["access_token"]!, (string?)json["refresh_token"] ?? refreshToken);
    }

    /// <summary>Microsoft 액세스 토큰으로 마인크래프트 계정 정보와 게임용 토큰을 받는다</summary>
    public static async Task<Account> LoginMinecraftAsync(string msAccessToken, CancellationToken ct)
    {
        // 1) Xbox Live
        var xbl = await PostJsonAsync("https://user.auth.xboxlive.com/user/authenticate", new JsonObject
        {
            ["Properties"] = new JsonObject { ["AuthMethod"] = "RPS", ["SiteName"] = "user.auth.xboxlive.com", ["RpsTicket"] = "d=" + msAccessToken },
            ["RelyingParty"] = "http://auth.xboxlive.com",
            ["TokenType"] = "JWT",
        }, ct);
        var xblToken = (string)xbl["Token"]!;
        var uhs = (string)xbl["DisplayClaims"]!["xui"]![0]!["uhs"]!;

        // 2) XSTS
        var xstsResp = await Http.PostAsJsonAsync("https://xsts.auth.xboxlive.com/xsts/authorize", new JsonObject
        {
            ["Properties"] = new JsonObject { ["SandboxId"] = "RETAIL", ["UserTokens"] = new JsonArray(xblToken) },
            ["RelyingParty"] = "rp://api.minecraftservices.com/",
            ["TokenType"] = "JWT",
        }, ct);
        var xsts = JsonNode.Parse(await xstsResp.Content.ReadAsStringAsync(ct))!;
        if (!xstsResp.IsSuccessStatusCode)
        {
            var err = (long?)xsts["XErr"] ?? 0;
            throw new AuthException(err switch
            {
                2148916233 => "이 Microsoft 계정에는 Xbox 계정이 없습니다. minecraft.net 에서 한 번 로그인해 프로필을 만드세요.",
                2148916238 => "미성년자 계정은 가족 그룹에 추가해야 로그인할 수 있습니다.",
                _ => $"Xbox 인증 실패 (XErr {err})",
            });
        }
        var xstsToken = (string)xsts["Token"]!;
        var xuid = (string?)xsts["DisplayClaims"]?["xui"]?[0]?["xid"] ?? "0";

        // 3) Minecraft
        var mcResp = await Http.PostAsJsonAsync("https://api.minecraftservices.com/authentication/login_with_xbox",
            new JsonObject { ["identityToken"] = $"XBL3.0 x={uhs};{xstsToken}" }, ct);
        if (!mcResp.IsSuccessStatusCode)
            throw new AuthException(mcResp.StatusCode == System.Net.HttpStatusCode.Forbidden
                ? "마인크래프트 로그인이 거부됐습니다. 이 앱 ID 가 아직 Mojang 의 Minecraft API 승인을 받지 않았을 수 있습니다."
                : $"마인크래프트 로그인 실패 ({(int)mcResp.StatusCode})");
        var mcToken = (string)JsonNode.Parse(await mcResp.Content.ReadAsStringAsync(ct))!["access_token"]!;

        // 4) 프로필 (게임을 가지고 있는지)
        using var req = new HttpRequestMessage(HttpMethod.Get, "https://api.minecraftservices.com/minecraft/profile");
        req.Headers.Authorization = new System.Net.Http.Headers.AuthenticationHeaderValue("Bearer", mcToken);
        var profResp = await Http.SendAsync(req, ct);
        if (profResp.StatusCode == System.Net.HttpStatusCode.NotFound)
            throw new AuthException("이 계정은 마인크래프트 Java 에디션을 가지고 있지 않습니다.");
        profResp.EnsureSuccessStatusCode();
        var prof = JsonNode.Parse(await profResp.Content.ReadAsStringAsync(ct))!;
        return new Account((string)prof["name"]!, (string)prof["id"]!, mcToken, xuid, IsOffline: false);
    }

    private static async Task<JsonNode> PostJsonAsync(string url, JsonObject body, CancellationToken ct)
    {
        var resp = await Http.PostAsJsonAsync(url, body, ct);
        var text = await resp.Content.ReadAsStringAsync(ct);
        if (!resp.IsSuccessStatusCode) throw new AuthException($"Xbox 로그인 실패 ({(int)resp.StatusCode})");
        return JsonNode.Parse(text)!;
    }
}
