using System.Globalization;
using System.IO;
using System.IO.Compression;
using System.Text.Json;
using System.Text.Json.Nodes;

namespace OptiClientLauncher;

/// <summary>다른 PvP 클라이언트(Lunar, Feather, Badlion, Silent)의 모드 프로필 하나</summary>
public sealed class ClientHud
{
    public required string Client { get; init; }
    public required string Name { get; init; }
    /// <summary>프로필 파일 (Lunar 는 프로필 폴더)</summary>
    public required string Path { get; init; }
    public bool Active { get; init; }
}

/// <summary>
/// 다른 클라이언트의 모드 프로필(어떤 HUD 를 켰는지, 화면 어디에 두었는지, 크기, 줌·퍼스펙티브 같은 기능)을
/// OptiClient HUD 설정(config/opticlient.json)으로 옮긴다. 계정 파일은 읽지 않는다.
/// </summary>
public static class ClientProfiles
{
    private static string AppData => Environment.GetFolderPath(Environment.SpecialFolder.ApplicationData);
    private static string Home => Environment.GetFolderPath(Environment.SpecialFolder.UserProfile);
    private static string Mc => System.IO.Path.Combine(AppData, ".minecraft");

    private sealed class HudMod
    {
        public bool? Enabled;
        public string? Anchor;
        public double X, Y;
        public double? Scale;
    }

    private sealed class Parsed
    {
        public readonly Dictionary<string, HudMod> Mods = new();
        public readonly Dictionary<string, bool> Flags = new();
    }

    // =====================================================================
    // 찾기
    // =====================================================================

    public static List<ImportSource> Scan()
    {
        var list = new List<ImportSource>();
        void Try(Action a)
        {
            try { a(); } catch (Exception) { /* 한 클라이언트가 이상해도 나머지는 보여 준다 */ }
        }
        var mcOptions = File.Exists(System.IO.Path.Combine(Mc, "options.txt")) ? System.IO.Path.Combine(Mc, "options.txt") : null;

        // Lunar: ~/.lunarclient/settings/game/<프로필>/mods.json (accounts.json 은 열지 않음)
        Try(() =>
        {
            var game = System.IO.Path.Combine(Home, ".lunarclient", "settings", "game");
            if (!Directory.Exists(game)) return;
            var active = new HashSet<string>();
            var display = new Dictionary<string, string>();
            var pm = System.IO.Path.Combine(game, "profile_manager.json");
            if (File.Exists(pm))
            {
                foreach (var p in JsonNode.Parse(File.ReadAllText(pm))!.AsArray())
                {
                    var n = (string?)p!["name"];
                    if (n == null) continue;
                    display[n] = (string?)p["displayName"] ?? n;
                    if ((bool?)p["active"] == true) active.Add(n);
                }
            }
            var lc = System.IO.Path.Combine(Mc, "optionsLC.txt");
            foreach (var dir in Directory.GetDirectories(game).OrderByDescending(d => active.Contains(System.IO.Path.GetFileName(d))))
            {
                if (!File.Exists(System.IO.Path.Combine(dir, "mods.json"))) continue;
                var name = System.IO.Path.GetFileName(dir);
                list.Add(new ImportSource
                {
                    Name = $"{display.GetValueOrDefault(name, name)}{(active.Contains(name) ? " (사용 중)" : "")}",
                    Launcher = "Lunar",
                    GameDir = Mc,
                    OptionsFile = File.Exists(lc) ? lc : mcOptions,
                    LunarOptions = File.Exists(lc),
                    HasMods = false,
                    Version = "여러 버전",
                    Hud = new ClientHud { Client = "Lunar", Name = name, Path = dir, Active = active.Contains(name) },
                });
            }
        });

        // Feather: .minecraft/feather/configuration/profiles/<프로필>.json (account.txt 는 열지 않음)
        Try(() =>
        {
            var conf = System.IO.Path.Combine(Mc, "feather", "configuration");
            var dir = System.IO.Path.Combine(conf, "profiles");
            if (!Directory.Exists(dir)) return;
            var current = File.Exists(System.IO.Path.Combine(conf, "current.profile")) ? File.ReadAllText(System.IO.Path.Combine(conf, "current.profile")).Trim() : "";
            var opts = System.IO.Path.Combine(Mc, "feather", "mc_options_v2.txt");
            foreach (var f in Directory.GetFiles(dir, "*.json"))
            {
                var name = System.IO.Path.GetFileNameWithoutExtension(f);
                var on = string.Equals(name, current, StringComparison.OrdinalIgnoreCase);
                list.Add(new ImportSource
                {
                    Name = name + (on ? " (사용 중)" : ""),
                    Launcher = "Feather",
                    GameDir = Mc,
                    OptionsFile = File.Exists(opts) ? opts : mcOptions,
                    HasMods = false,
                    Version = "여러 버전",
                    Hud = new ClientHud { Client = "Feather", Name = name, Path = f, Active = on },
                });
            }
        });

        // Badlion: .minecraft/BLClient-Mod-Profiles/<프로필>.zip (accounts.dat 은 열지 않음)
        Try(() =>
        {
            var dir = System.IO.Path.Combine(Mc, "BLClient-Mod-Profiles");
            if (!Directory.Exists(dir)) return;
            foreach (var f in Directory.GetFiles(dir, "*.zip"))
            {
                string name = System.IO.Path.GetFileNameWithoutExtension(f);
                var on = false;
                try
                {
                    using var zip = ZipFile.OpenRead(f);
                    if (zip.GetEntry("data.json") == null) continue;
                    if (zip.GetEntry("info.json") is { } info)
                    {
                        using var s = info.Open();
                        var j = JsonNode.Parse(s)!;
                        name = (string?)j["name"] ?? name;
                        on = (bool?)j["active"] == true;
                    }
                }
                catch (InvalidDataException) { continue; }
                list.Add(new ImportSource
                {
                    Name = name + (on ? " (사용 중)" : ""),
                    Launcher = "Badlion",
                    GameDir = Mc,
                    OptionsFile = mcOptions,
                    HasMods = false,
                    Version = "여러 버전",
                    Hud = new ClientHud { Client = "Badlion", Name = name, Path = f, Active = on },
                });
            }
        });

        // Silent: .minecraft/silent/configs/<설정>.json
        Try(() =>
        {
            var dir = System.IO.Path.Combine(Mc, "silent", "configs");
            if (!Directory.Exists(dir)) return;
            var sel = System.IO.Path.Combine(dir, "selected_config.txt");
            var current = File.Exists(sel) ? File.ReadAllText(sel).Trim() : "";
            foreach (var f in Directory.GetFiles(dir, "*.json"))
            {
                var on = string.Equals(System.IO.Path.GetFileName(f), current, StringComparison.OrdinalIgnoreCase);
                list.Add(new ImportSource
                {
                    Name = System.IO.Path.GetFileNameWithoutExtension(f) + (on ? " (사용 중)" : ""),
                    Launcher = "Silent",
                    GameDir = Mc,
                    OptionsFile = mcOptions,
                    HasMods = false,
                    Version = "여러 버전",
                    Hud = new ClientHud { Client = "Silent", Name = System.IO.Path.GetFileNameWithoutExtension(f), Path = f, Active = on },
                });
            }
        });

        // 사용 중인 프로필을 먼저
        return list.OrderByDescending(s => s.Hud!.Active).ToList();
    }

    // =====================================================================
    // 읽기
    // =====================================================================

    private static double Num(JsonNode? n, double def = 0)
    {
        if (n == null) return def;
        if (n is JsonValue v)
        {
            if (v.TryGetValue<double>(out var d)) return d;
            if (v.TryGetValue<string>(out var s) && double.TryParse(s, NumberStyles.Float, CultureInfo.InvariantCulture, out var p)) return p;
        }
        return def;
    }

    private static bool? Bool(JsonNode? n)
    {
        if (n is not JsonValue v) return null;
        if (v.TryGetValue<bool>(out var b)) return b;
        if (v.TryGetValue<string>(out var s)) return s.Equals("true", StringComparison.OrdinalIgnoreCase) ? true : s.Equals("false", StringComparison.OrdinalIgnoreCase) ? false : null;
        return null;
    }

    // ---------------- Lunar ----------------

    private static readonly Dictionary<string, string> LunarHud = new()
    {
        ["FPS"] = "fps", ["CPS"] = "cps", ["PING"] = "ping", ["COORDINATES"] = "coords", ["CLOCK"] = "clock",
        ["POTION_EFFECTS"] = "potions", ["ARMORSTATUS"] = "armor", ["KEYSTROKES"] = "keystrokes", ["REACH_DISPLAY"] = "reach",
        ["COMBO"] = "combo", ["ITEM_COUNTER"] = "items", ["DIRECTION_HUD"] = "compassbar", ["SATURATION"] = "hunger",
        ["MEMORY"] = "memory", ["SERVER_ADDRESS"] = "server",
    };

    /// <summary>topLeft / bottomCenterRight … → tl / bc …</summary>
    private static string? LunarAnchor(string? p)
    {
        if (string.IsNullOrEmpty(p)) return null;
        var v = p.StartsWith("bottom") ? 'b' : p.StartsWith("middle") ? 'c' : 't';
        var rest = p[(p.StartsWith("middle") ? 6 : p.StartsWith("bottom") ? 6 : 3)..];
        var h = rest.StartsWith("Left") ? 'l' : rest.StartsWith("Right") ? 'r' : 'c';
        return $"{v}{h}";
    }

    private static Parsed ReadLunar(ClientHud c)
    {
        var r = new Parsed();
        var d = JsonNode.Parse(File.ReadAllText(System.IO.Path.Combine(c.Path, "mods.json")))!.AsObject();
        foreach (var (key, id) in LunarHud)
        {
            var node = d[key];
            // Lunar 는 켠 HUD 만 "enabled": true 로 저장한다 (없으면 꺼짐)
            var m = new HudMod { Enabled = Bool(node?["enabled"]) ?? false };
            JsonNode? pos = node;
            if (node is JsonObject obj && (obj["position"] == null || key == "ARMORSTATUS"))
            {
                // 위치가 하위 항목에 있는 모드 (PING → PING_HUD, 갑옷은 칸마다) → 가장 위 칸
                pos = obj.Where(kv => kv.Value is JsonObject c && c["position"] != null)
                    .Select(kv => kv.Value).OrderBy(v => Num(v!["y"])).FirstOrDefault() ?? node;
            }
            if (pos?["position"] != null)
            {
                m.Anchor = LunarAnchor((string?)pos["position"]);
                m.X = Num(pos["x"]);
                m.Y = Num(pos["y"]);
            }
            var scale = node?["options"]?["scale"];
            if (scale != null) m.Scale = Num(scale, 1);
            r.Mods[id] = m;
        }
        // 토글 달리기/웅크리기 + 상태 표시
        var ts = d["TOGGLE_SNEAK"];
        if (Bool(ts?["enabled"]) is { } tsOn)
        {
            r.Flags["toggleSprint"] = tsOn;
            r.Flags["toggleSneak"] = tsOn;
        }
        var tsHud = ts?["TOGGLE_SNEAK_HUD_CHILD"];
        var sprint = new HudMod { Enabled = (Bool(ts?["enabled"]) ?? false) && (Bool(tsHud?["enabled"]) ?? true) };
        if (tsHud?["position"] != null)
        {
            sprint.Anchor = LunarAnchor((string?)tsHud["position"]);
            sprint.X = Num(tsHud["x"]);
            sprint.Y = Num(tsHud["y"]);
        }
        r.Mods["sprint"] = sprint;

        void Flag(string key, string flag, bool? missing = null)
        {
            var v = Bool(d[key]?["enabled"]) ?? missing;
            if (v != null) r.Flags[flag] = v.Value;
        }
        Flag("ZOOM", "zoomEnabled", true); // Lunar 줌은 기본으로 켜져 있음
        Flag("FREELOOK", "freelook");
        Flag("HITBOX", "hitboxes");
        Flag("CROSSHAIR", "crosshair");
        Flag("HURT_CAM", "noHurtCam");
        return r;
    }

    // ---------------- Feather ----------------

    private static readonly Dictionary<string, string> FeatherHud = new()
    {
        ["fps"] = "fps", ["cps"] = "cps", ["ping"] = "ping", ["coordinates"] = "coords", ["time"] = "clock",
        ["potionEffects"] = "potions", ["armorStatus"] = "armor", ["keystrokes"] = "keystrokes", ["reachDisplay"] = "reach",
        ["comboDisplay"] = "combo", ["itemCounter"] = "items", ["direction"] = "compassbar", ["saturation"] = "hunger",
        ["systemresources"] = "memory", ["serverAddress"] = "server", ["speedMeter"] = "speed", ["toggleSprint"] = "sprint",
    };

    /// <summary>top_left / center / bottom_center … → tl / cc / bc …</summary>
    private static string? FeatherAnchor(string? a)
    {
        if (string.IsNullOrEmpty(a)) return null;
        if (a == "center") return "cc";
        var parts = a.Split('_');
        if (parts.Length != 2) return null;
        var v = parts[0] switch { "top" => 't', "bottom" => 'b', _ => 'c' };
        var h = parts[1] switch { "left" => 'l', "right" => 'r', _ => 'c' };
        return $"{v}{h}";
    }

    private static Parsed ReadFeather(ClientHud c)
    {
        var r = new Parsed();
        var d = JsonNode.Parse(File.ReadAllText(c.Path))!.AsObject();
        foreach (var (key, id) in FeatherHud)
        {
            var node = d[key];
            if (node == null) continue;
            var on = Bool(node["enabled"]) ?? false;
            var m = new HudMod { Enabled = on && (Bool(node["hudEnabled1"]) ?? true) };
            if (node["hudAnchor"] != null)
            {
                m.Anchor = FeatherAnchor((string?)node["hudAnchor"]);
                m.X = Num(node["hudRelativeX"]);
                m.Y = Num(node["hudRelativeY"]);
            }
            if (node["hudScale"] != null) m.Scale = Num(node["hudScale"], 1);
            r.Mods[id] = m;
        }
        if (Bool(d["toggleSprint"]?["enabled"]) is { } ts)
        {
            r.Flags["toggleSprint"] = ts;
            r.Flags["toggleSneak"] = ts;
        }
        void Flag(string key, string flag)
        {
            if (Bool(d[key]?["enabled"]) is { } v) r.Flags[flag] = v;
        }
        Flag("zoom", "zoomEnabled");
        Flag("perspective", "freelook");
        Flag("hitbox", "hitboxes");
        Flag("crosshair", "crosshair");
        Flag("brightness", "fullbright");
        return r;
    }

    // ---------------- Badlion ----------------

    private static readonly Dictionary<string, string> BadlionHud = new()
    {
        ["showFPS"] = "fps", ["showCPS"] = "cps", ["showPing"] = "ping", ["coordinates"] = "coords", ["clock"] = "clock",
        ["potionStatus"] = "potions", ["armorStatus"] = "armor", ["keyStroke"] = "keystrokes", ["reachDisplay"] = "reach",
        ["comboCounter"] = "combo", ["itemCounter"] = "items", ["showDirection"] = "compassbar", ["saturation"] = "hunger",
        ["memoryDisplay"] = "memory", ["serverAddress"] = "server", ["speedometer"] = "speed", ["toggleSprint"] = "sprint",
    };

    private static Parsed ReadBadlion(ClientHud c)
    {
        var r = new Parsed();
        using var zip = ZipFile.OpenRead(c.Path);
        JsonObject d;
        using (var s = zip.GetEntry("data.json")!.Open()) d = JsonNode.Parse(s)!.AsObject();
        // Badlion 은 위치를 프로필에 남기지 않아서 켜짐만 가져온다 (위치는 OptiClient 기본 배치)
        foreach (var (key, id) in BadlionHud)
            if (Bool(d[key]?["enabled"]) is { } on) r.Mods[id] = new HudMod { Enabled = on };
        if (Bool(d["toggleSprint"]?["enabled"]) is { } ts)
        {
            r.Flags["toggleSprint"] = ts;
            r.Flags["toggleSneak"] = ts;
        }
        void Flag(string key, string flag)
        {
            if (Bool(d[key]?["enabled"]) is { } v) r.Flags[flag] = v;
        }
        Flag("zoom", "zoomEnabled");
        Flag("perspective", "freelook");
        Flag("hitboxes", "hitboxes");
        Flag("crosshair", "crosshair");
        Flag("fullbright", "fullbright");
        return r;
    }

    // ---------------- Silent ----------------

    private static readonly Dictionary<string, string> SilentHud = new()
    {
        ["fps_display"] = "fps", ["cps_display"] = "cps", ["ping_display"] = "ping", ["coords"] = "coords", ["clock"] = "clock",
        ["potion_status"] = "potions", ["armor_status"] = "armor", ["keystrokes"] = "keystrokes", ["reach_display"] = "reach",
        ["combo_counter"] = "combo", ["potion_counter"] = "items", ["compass"] = "compassbar", ["saturation"] = "hunger",
        ["memory"] = "memory", ["server_address"] = "server", ["speedometer"] = "speed", ["toggle_sprint"] = "sprint",
    };

    private static Parsed ReadSilent(ClientHud c)
    {
        var r = new Parsed();
        var mods = JsonNode.Parse(File.ReadAllText(c.Path))!["mods"]!.AsObject();
        // Silent 은 화면 픽셀 좌표로 저장 → 게임 GUI 좌표로 (GUI 크기로 나눔)
        var gui = GuiScale();
        foreach (var (key, id) in SilentHud)
        {
            var node = mods[$"mods.{key}.name"];
            if (node == null) continue;
            var m = new HudMod { Enabled = Bool(node["toggle"]) ?? false };
            if (node["x"] != null && node["y"] != null)
            {
                m.Anchor = "tl";
                m.X = Num(node["x"]) / gui;
                m.Y = Num(node["y"]) / gui;
            }
            r.Mods[id] = m;
        }
        if (Bool(mods["mods.toggle_sprint.name"]?["toggle"]) is { } ts)
        {
            r.Flags["toggleSprint"] = ts;
            r.Flags["toggleSneak"] = Bool(mods["mods.toggle_sneak.name"]?["toggle"]) ?? ts;
        }
        void Flag(string key, string flag)
        {
            if (Bool(mods[$"mods.{key}.name"]?["toggle"]) is { } v) r.Flags[flag] = v;
        }
        Flag("zoom", "zoomEnabled");
        Flag("freelook", "freelook");
        Flag("hitbox", "hitboxes");
        Flag("crosshair", "crosshair");
        Flag("fullbright", "fullbright");
        return r;
    }

    /// <summary>.minecraft 의 GUI 크기 (자동이면 1920×1080 기준 4)</summary>
    private static double GuiScale()
    {
        try
        {
            var line = File.ReadLines(System.IO.Path.Combine(Mc, "options.txt")).FirstOrDefault(l => l.StartsWith("guiScale:"));
            if (line != null && int.TryParse(line[9..], out var g) && g > 0) return g;
        }
        catch (IOException) { }
        return 4;
    }

    // =====================================================================
    // 쓰기
    // =====================================================================

    /// <summary>모듈 기본 위치 값 (Modules.java: -3 자동 정렬, -1 핫바 옆, -2 오른쪽 아래, -4 크로스헤어 옆, -5 위 가운데)</summary>
    private static readonly Dictionary<string, int> DefaultPos = new()
    {
        ["armor"] = -1, ["keystrokes"] = -2, ["targethud"] = -4, ["compassbar"] = -5,
    };

    private static readonly Dictionary<string, string> FlagNames = new()
    {
        ["zoomEnabled"] = "줌", ["freelook"] = "퍼스펙티브", ["hitboxes"] = "히트박스", ["crosshair"] = "크로스헤어",
        ["noHurtCam"] = "흔들림 끄기", ["fullbright"] = "밝기 최대", ["toggleSprint"] = "달리기 토글", ["toggleSneak"] = "웅크리기 토글",
    };

    /// <summary>OptiClient HUD 의 대략 크기 (GUI 좌표, 크기 배율 1)</summary>
    private static (int W, int H) SizeOf(string id) => id switch
    {
        "keystrokes" => (70, 82),
        "armor" => (50, 100),
        "coords" => (120, 37),
        "potions" => (100, 27),
        "items" => (60, 40),
        "compassbar" => (160, 14),
        _ => (90, 17),
    };

    /// <summary>
    /// OptiClient HUD 가 원래 클라이언트보다 크면(예: 키 입력 56 → 82) 바로 아래에 붙여 둔 HUD 를 덮는다.
    /// 같은 기준(위/아래)끼리 원래 순서를 지키면서 겹치는 것만 밀어낸다 (위 기준은 아래로, 아래 기준은 위로).
    /// </summary>
    private static int ResolveOverlaps(Parsed p)
    {
        const int sw = 960, sh = 540, gap = 2; // 1920×1080, GUI 크기 2 기준
        var placed = p.Mods.Where(kv => kv.Value.Enabled == true && kv.Value.Anchor != null)
            .Select(kv =>
            {
                var (w, h) = SizeOf(kv.Key);
                var sc = kv.Value.Scale ?? 1;
                w = (int)(w * sc);
                h = (int)(h * sc);
                var a = kv.Value.Anchor!;
                double bx = a[1] == 'r' ? sw - w : a[1] == 'c' ? (sw - w) / 2.0 : 0;
                double by = a[0] == 'b' ? sh - h : a[0] == 'c' ? (sh - h) / 2.0 : 0;
                var x = Math.Clamp(bx + kv.Value.X, 0, sw - w);
                var y = Math.Clamp(by + kv.Value.Y, 0, sh - h);
                return (Id: kv.Key, Mod: kv.Value, Up: a[0] == 'b', X: x, Y: y, W: w, H: h);
            }).ToList();

        var moved = 0;
        foreach (var up in new[] { false, true })
        {
            var group = placed.Where(t => t.Up == up).OrderBy(t => up ? -(t.Y + t.H) : t.Y).ToList();
            var done = new List<(double X, double Y, int W, int H)>();
            foreach (var t in group)
            {
                var y = t.Y;
                bool again = true;
                for (var guard = 0; again && guard < 20; guard++)
                {
                    again = false;
                    foreach (var o in done)
                    {
                        var xOverlap = t.X < o.X + o.W && o.X < t.X + t.W;
                        var yOverlap = y < o.Y + o.H && o.Y < y + t.H;
                        if (!xOverlap || !yOverlap) continue;
                        y = up ? o.Y - t.H - gap : o.Y + o.H + gap;
                        again = true;
                    }
                }
                if (Math.Abs(y - t.Y) > 0.5)
                {
                    t.Mod.Y += y - t.Y;
                    moved++;
                }
                done.Add((t.X, y, t.W, t.H));
            }
        }
        return moved;
    }

    /// <summary>클라이언트 프로필을 읽어 대상 게임 폴더의 config/opticlient.json 에 합친다</summary>
    public static void Apply(ClientHud c, string gameDir, IProgress<string> log)
    {
        var p = c.Client switch
        {
            "Lunar" => ReadLunar(c),
            "Feather" => ReadFeather(c),
            "Badlion" => ReadBadlion(c),
            _ => ReadSilent(c),
        };

        var moved = ResolveOverlaps(p);
        var file = System.IO.Path.Combine(gameDir, "config", "opticlient.json");
        Directory.CreateDirectory(System.IO.Path.GetDirectoryName(file)!);
        var cfg = File.Exists(file) ? JsonNode.Parse(File.ReadAllText(file))!.AsObject() : new JsonObject();
        cfg["layoutVersion"] = 2; // 낮으면 게임이 위치를 기본값으로 되돌림
        var modules = cfg["modules"] as JsonObject ?? new JsonObject();
        cfg["modules"] = modules;

        var on = new List<string>();
        foreach (var (id, m) in p.Mods)
        {
            var s = modules[id] as JsonObject ?? new JsonObject();
            modules[id] = s;
            if (m.Enabled != null) s["enabled"] = m.Enabled.Value;
            if (m.Anchor != null)
            {
                s["anchor"] = m.Anchor;
                s["x"] = (int)Math.Round(m.X);
                s["y"] = (int)Math.Round(m.Y);
            }
            else if (s["x"] == null)
            {
                // 위치 정보가 없으면 OptiClient 기본 위치 (자동 정렬 등)
                var def = DefaultPos.GetValueOrDefault(id, -3);
                s["x"] = def;
                s["y"] = def;
            }
            if (m.Scale is { } sc) s["scale"] = Math.Clamp(sc, 0.5, 2.0);
            if (m.Enabled == true) on.Add(id);
        }
        foreach (var (flag, v) in p.Flags) cfg[flag] = v;

        File.WriteAllText(file, cfg.ToJsonString(new JsonSerializerOptions { WriteIndented = true }));
        log.Report($"{c.Client} '{c.Name}' 프로필의 HUD {p.Mods.Count}개를 옮겼어요 (켜짐: {(on.Count == 0 ? "없음" : string.Join(", ", on))}).");
        var flags = p.Flags.Select(kv => $"{FlagNames.GetValueOrDefault(kv.Key, kv.Key)} {(kv.Value ? "켬" : "끔")}").ToList();
        if (flags.Count > 0) log.Report("기능: " + string.Join(", ", flags));
        if (moved > 0) log.Report($"OptiClient HUD 가 조금 더 커서 겹치는 {moved}개는 순서를 지켜 살짝 옮겼어요.");
    }
}
