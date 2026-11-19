package dev.opticlient.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** 기본 HUD 모듈 모음. */
public final class Modules {
    /** 왼쪽 위에 자동으로 쌓이는 위치 */
    static final int AUTO = -3;

    private Modules() {
    }

    public static List<Module> createAll() {
        List<Module> list = new ArrayList<Module>();
        list.add(new Fps());
        list.add(new Cps());
        list.add(new Ping());
        list.add(new Coords());
        list.add(new Speed());
        list.add(new Clock());
        list.add(new SprintStatus());
        list.add(new Potions());
        list.add(new Armor());
        list.add(new Keystrokes());
        list.add(new Reach());
        list.add(new Combo());
        list.add(new TargetHud());
        list.add(new ItemCounter());
        list.add(new CompassBar());
        list.add(new Hunger());
        list.add(new Memory());
        list.add(new Server());
        list.add(new Inventory());
        list.add(new Aim());
        return list;
    }

    static final class Fps extends Module.TextModule {
        Fps() {
            super("fps", "FPS", true, AUTO, AUTO);
            meta("HUD", "초당 프레임 수", Icons.FPS);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return Collections.singletonList("FPS: " + g.fps());
        }
    }

    static final class Cps extends Module.TextModule {
        Cps() {
            super("cps", "CPS", true, AUTO, AUTO);
            meta("PvP", "좌/우클릭 초당 횟수", Icons.MOUSE);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return Collections.singletonList("CPS: " + OptiCore.CPS.left() + (opt("rmb") ? " | " + OptiCore.CPS.right() : ""));
        }

        @Override
        public List<Option> options() {
            return Collections.singletonList(new Option("rmb", "오른쪽 클릭 CPS 표시", true));
        }
    }

    static final class Ping extends Module.TextModule {
        Ping() {
            super("ping", "핑", true, AUTO, AUTO);
            meta("PvP", "서버 응답 시간", Icons.SIGNAL);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            int p = g.ping();
            return Collections.singletonList("Ping: " + (p < 0 ? "-" : p + "ms"));
        }
    }

    static final class Coords extends Module.TextModule {
        private static final String[] DIRS = {"남 (+Z)", "서 (-X)", "북 (-Z)", "동 (+X)"};

        Coords() {
            super("coords", "좌표", true, AUTO, AUTO);
            meta("HUD", "XYZ · 방향 · 바이옴", Icons.COMPASS);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            float yaw = ((g.yaw() % 360f) + 360f) % 360f;
            int dir = (int) Math.floor(yaw / 90f + 0.5f) & 3;
            List<String> out = new ArrayList<String>();
            out.add(String.format(Locale.ROOT, "XYZ: %.1f / %.0f / %.1f", g.x(), Math.floor(g.y()), g.z()));
            if (opt("dir")) out.add("방향: " + DIRS[dir]);
            if (opt("biome")) out.add("바이옴: " + g.biome());
            return out;
        }

        @Override
        public List<Option> options() {
            return Arrays.asList(new Option("dir", "방향 표시", true), new Option("biome", "바이옴 표시", true));
        }
    }

    static final class Speed extends Module.TextModule {
        Speed() {
            super("speed", "속도", false, AUTO, AUTO);
            meta("HUD", "이동 속도 (블록/초)", Icons.SPEED);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return Collections.singletonList(String.format(Locale.ROOT, "속도: %.2f b/s", g.speed()));
        }
    }

    static final class Clock extends Module.TextModule {
        private final SimpleDateFormat hm = new SimpleDateFormat("HH:mm");
        private final SimpleDateFormat hms = new SimpleDateFormat("HH:mm:ss");
        private final SimpleDateFormat ampm = new SimpleDateFormat("a h:mm", Locale.KOREAN);

        Clock() {
            super("clock", "시계", false, AUTO, AUTO);
            meta("HUD", "현재 시각", Icons.CLOCK);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            SimpleDateFormat f = opt("ampm") ? ampm : opt("sec") ? hms : hm;
            return Collections.singletonList("시간: " + f.format(new Date()));
        }

        @Override
        public List<Option> options() {
            return Arrays.asList(new Option("sec", "초까지 표시", false), new Option("ampm", "오전/오후로 표시", false));
        }
    }

    static final class SprintStatus extends Module.TextModule {
        SprintStatus() {
            super("sprint", "토글 상태", true, AUTO, AUTO);
            meta("PvP", "달리기·웅크리기 고정, 퍼스펙티브 표시", Icons.RUN);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            List<String> out = new ArrayList<String>();
            if (OptiCore.config.toggleSprint && OptiCore.sprintToggled) out.add("[달리기 고정]");
            if (OptiCore.config.toggleSneak && OptiCore.sneakToggled) out.add("[웅크리기 고정]");
            if (OptiCore.freelook) out.add("[퍼스펙티브]");
            return out;
        }

        @Override
        protected List<String> preview() {
            return Collections.singletonList("[달리기 고정]");
        }
    }

    static final class Potions extends Module.TextModule {
        Potions() {
            super("potions", "포션 효과", true, AUTO, AUTO);
            meta("PvP", "효과 · 레벨 · 남은 시간", Icons.POTION);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return g.effects();
        }

        @Override
        protected List<String> preview() {
            return Arrays.asList("신속 II: 1:30", "힘 I: 0:08");
        }

        @Override
        public List<Option> options() {
            return Collections.singletonList(new Option("ending", "10초 안에 끝나는 효과 빨갛게", true));
        }

        @Override
        protected int valueColor(String line, HudConfig cfg) {
            if (!opt("ending")) return cfg.textColor;
            // "신속 II: 1:23" → 남은 초
            int c = line.lastIndexOf(':');
            int c2 = c > 0 ? line.lastIndexOf(' ', c) : -1;
            try {
                int min = Integer.parseInt(line.substring(c2 + 1, c).trim());
                int sec = Integer.parseInt(line.substring(c + 1).trim());
                if (min * 60 + sec <= 10) return (System.currentTimeMillis() / 400) % 2 == 0 ? 0xFFFF5555 : 0xFFFFD166;
            } catch (RuntimeException ignored) {
                // ∞ 등
            }
            return cfg.textColor;
        }
    }

    /** 갑옷 + 손에 든 아이템과 내구도 */
    static final class Armor extends Module {
        private static final int ROW = 18;

        Armor() {
            super("armor", "갑옷 상태", true, -1, -1); // -1 = 핫바 오른쪽 기본 위치
            meta("PvP", "갑옷 · 무기 내구도", Icons.ARMOR);
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            Object[] all = {g.armor(0), g.armor(1), g.armor(2), g.armor(3), opt("held") ? g.mainHand() : null};
            java.util.List<Object> items = new java.util.ArrayList<Object>();
            for (Object it : all) if (it != null) items.add(it);

            boolean modern = Theme.modern();
            int pad = modern && cfg.background ? 3 : 0;
            if (items.isEmpty()) {
                if (!editing) {
                    lastW = lastH = 0;
                    return;
                }
                Theme.panel(r, x, y, 64, 26);
                r.text("갑옷 HUD", x + Theme.padLeft(), y + 9, Theme.WHITE, cfg.shadow);
                lastW = 64;
                lastH = 26;
                return;
            }

            int w = 0;
            for (Object it : items) w = Math.max(w, 20 + Math.max(r.width(label(g, it)), modern ? 22 : 0));
            w += pad * 2;
            int h = items.size() * ROW - 2 + pad * 2;
            if (modern && cfg.background) Theme.round(r, x, y, x + w, y + h, Theme.HUD_BG, 3);

            for (int i = 0; i < items.size(); i++) {
                Object it = items.get(i);
                int rx = x + pad;
                int ry = y + pad + i * ROW;
                r.item(it, rx, ry);
                int max = g.maxDamage(it);
                String text = label(g, it);
                if (modern && max > 0) {
                    float ratio = (max - g.damage(it)) / (float) max;
                    r.text(text, rx + 20, ry + 1, cfg.textColor, cfg.shadow);
                    // 내구도 막대
                    r.fill(rx + 20, ry + 11, rx + 42, ry + 13, 0x50FFFFFF);
                    r.fill(rx + 20, ry + 11, rx + 20 + Math.max(1, Math.round(22 * ratio)), ry + 13, colorFor(ratio));
                } else if (text.length() > 0) {
                    float ratio = max > 0 ? (max - g.damage(it)) / (float) max : 1f;
                    r.text(text, rx + 20, ry + 4, colorFor(ratio), cfg.shadow);
                }
            }
            lastW = w;
            lastH = h;
        }

        @Override
        public List<Option> options() {
            return Collections.singletonList(new Option("held", "손에 든 아이템 표시", true));
        }

        private static String label(GameInfo g, Object it) {
            int max = g.maxDamage(it);
            // 개수는 아이템 아이콘 위에 이미 표시되므로 내구도만 글자로 쓴다
            return max > 0 ? String.valueOf(max - g.damage(it)) : "";
        }

        private static int colorFor(float ratio) {
            if (ratio > 0.5f) return 0xFF6EE7A0;
            if (ratio > 0.2f) return 0xFFFFD166;
            return 0xFFFF6B6B;
        }
    }

    /** WASD / 마우스 / 스페이스 입력 표시 (누르면 부드럽게 색이 바뀐다) */
    static final class Keystrokes extends Module {
        private static final int B = 22;
        private static final int GAP = 2;
        private final float[] anim = new float[GameInfo.Key.values().length];
        private long lastNanos;

        Keystrokes() {
            super("keystrokes", "키 입력", true, -2, -2); // -2 = 화면 오른쪽 아래 기본 위치
            meta("PvP", "WASD · 마우스 · 스페이스", Icons.KEYS);
        }

        /** 눌림 상태를 0~1 사이 값으로 부드럽게 따라간다 (약 80ms) */
        private float press(GameInfo g, GameInfo.Key k, float dt) {
            float target = g.key(k) ? 1f : 0f;
            float v = anim[k.ordinal()];
            float step = dt * 12f;
            v = target > v ? Math.min(target, v + step) : Math.max(target, v - step);
            anim[k.ordinal()] = v;
            return v;
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            long now = System.nanoTime();
            float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
            lastNanos = now;

            int full = B * 3 + GAP * 2;
            key(r, cfg, "W", press(g, GameInfo.Key.FORWARD, dt), x + B + GAP, y, B, B);
            int y2 = y + B + GAP;
            key(r, cfg, "A", press(g, GameInfo.Key.LEFT, dt), x, y2, B, B);
            key(r, cfg, "S", press(g, GameInfo.Key.BACK, dt), x + B + GAP, y2, B, B);
            key(r, cfg, "D", press(g, GameInfo.Key.RIGHT, dt), x + (B + GAP) * 2, y2, B, B);
            int y3 = y2 + B + GAP;
            int bottom = y2 + B;
            if (opt("mouse")) {
                int half = (full - GAP) / 2;
                mouse(r, cfg, "LMB", OptiCore.CPS.left(), press(g, GameInfo.Key.ATTACK, dt), x, y3, half);
                mouse(r, cfg, "RMB", OptiCore.CPS.right(), press(g, GameInfo.Key.USE, dt), x + half + GAP, y3, full - half - GAP);
                bottom = y3 + B;
                y3 += B + GAP;
            }
            if (opt("space")) {
                float jump = press(g, GameInfo.Key.JUMP, dt);
                Theme.key(r, x, y3, full, 10, jump);
                r.fill(x + full / 2 - 12, y3 + 4, x + full / 2 + 12, y3 + 6, Theme.keyText(jump));
                bottom = y3 + 10;
            }
            lastW = full;
            lastH = bottom - y;
        }

        @Override
        public List<Option> options() {
            return Arrays.asList(new Option("mouse", "마우스 버튼 (CPS) 표시", true), new Option("space", "스페이스 표시", true));
        }

        private static void key(HudRenderer r, HudConfig cfg, String s, float p, int x, int y, int w, int h) {
            Theme.key(r, x, y, w, h, p);
            int c = Theme.minimal() && p > 0.5f ? Theme.accent(x, y) : Theme.keyText(p);
            r.centered(s, x + w / 2, y + (h - 8) / 2, c, cfg.shadow && p < 0.5f);
        }

        private static void mouse(HudRenderer r, HudConfig cfg, String s, int cps, float p, int x, int y, int w) {
            Theme.key(r, x, y, w, B, p);
            int c = Theme.minimal() && p > 0.5f ? Theme.accent(x, y) : Theme.keyText(p);
            boolean sh = cfg.shadow && p < 0.5f;
            r.centered(s, x + w / 2, y + 3, c, sh);
            int sub = p > 0.5f ? c : (Theme.modern() ? Theme.MUTED : Theme.accent(x, y));
            r.centered(cps + " CPS", x + w / 2, y + 12, sub, sh);
        }
    }

    // =====================================================================
    // PvP 모듈
    // =====================================================================

    /** 마지막으로 맞힌 거리 */
    static final class Reach extends Module.TextModule {
        Reach() {
            super("reach", "리치", true, AUTO, AUTO);
            meta("PvP", "마지막으로 맞힌 거리 (블록)", Icons.TARGET);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            double d = OptiCore.PVP.reach();
            return Collections.singletonList("리치: " + (d < 0 ? "-" : String.format(Locale.ROOT, "%.2f", d)));
        }
    }

    /** 맞지 않고 연속으로 맞힌 횟수 */
    static final class Combo extends Module.TextModule {
        Combo() {
            super("combo", "콤보", true, AUTO, AUTO);
            meta("PvP", "맞지 않고 연속으로 맞힌 횟수", Icons.COMBO);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return Collections.singletonList("콤보: " + OptiCore.PVP.combo());
        }
    }

    /** 최근에 때린 상대의 이름·체력·거리 */
    static final class TargetHud extends Module {
        private static final Target PREVIEW = new Target();

        static {
            PREVIEW.name = "Steve";
            PREVIEW.health = 14f;
            PREVIEW.maxHealth = 20f;
            PREVIEW.distance = 3.1;
            PREVIEW.player = true;
        }

        TargetHud() {
            super("targethud", "타깃 HUD", true, -4, -4);
            meta("PvP", "때린 상대의 체력·거리", Icons.PLAYER);
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            Target t = OptiCore.PVP.showTarget() ? OptiCore.PVP.target : editing ? PREVIEW : null;
            if (t == null) {
                lastW = lastH = 0;
                return;
            }
            int w = 124;
            int h = 36;
            Theme.panel(r, x, y, w, h);
            int px = x + 6;
            // 이름 + 거리
            String name = t.name.length() > 16 ? t.name.substring(0, 16) : t.name;
            r.text(name, px, y + 5, Theme.WHITE, cfg.shadow);
            String dist = String.format(Locale.ROOT, "%.1fm", t.distance);
            r.text(dist, x + w - 6 - r.width(dist), y + 5, Theme.MUTED, cfg.shadow);
            // 체력 막대
            float max = Math.max(1f, t.maxHealth);
            float ratio = Math.max(0f, Math.min(1f, t.health / max));
            int bx2 = x + w - 6;
            r.fill(px, y + 17, bx2, y + 21, 0x50FFFFFF);
            r.fill(px, y + 17, px + Math.round((bx2 - px) * ratio), y + 21, hpColor(ratio));
            // 체력 수치 + 나와 비교
            String hp = String.format(Locale.ROOT, "%.1f / %.0f", t.health, max);
            r.text(hp, px, y + 25, Theme.TEXT, cfg.shadow);
            if (!editing || t != PREVIEW) {
                float mine = g.health();
                String cmp = mine > t.health + 0.5f ? "우세" : mine < t.health - 0.5f ? "열세" : "비슷";
                int cc = mine > t.health + 0.5f ? 0xFF6EE7A0 : mine < t.health - 0.5f ? 0xFFFF6B6B : 0xFFFFD166;
                r.text(cmp, x + w - 6 - r.width(cmp), y + 25, cc, cfg.shadow);
            }
            lastW = w;
            lastH = h;
        }

        private static int hpColor(float ratio) {
            if (ratio > 0.5f) return 0xFF6EE7A0;
            if (ratio > 0.25f) return 0xFFFFD166;
            return 0xFFFF6B6B;
        }
    }

    /** PvP 소모품 개수 */
    static final class ItemCounter extends Module.TextModule {
        private static final String[][] ITEMS = {
                {"포션", "splash_potion"},
                {"마시는 포션", "potion"},
                {"황금사과", "golden_apple", "enchanted_golden_apple"},
                {"엔더 진주", "ender_pearl"},
                {"화살", "arrow", "spectral_arrow", "tipped_arrow"},
                {"토템", "totem_of_undying"},
                {"거미줄", "cobweb", "web"},
                {"물 양동이", "water_bucket"},
        };

        ItemCounter() {
            super("items", "아이템 개수", true, AUTO, AUTO);
            meta("PvP", "포션·황금사과·진주·화살 개수", Icons.BAG);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            java.util.Map<String, Integer> inv = g.inventory();
            List<String> out = new ArrayList<String>();
            for (String[] it : ITEMS) {
                int n = 0;
                for (int i = 1; i < it.length; i++) {
                    Integer c = inv.get(it[i]);
                    if (c != null) n += c;
                }
                if (n > 0) out.add(it[0] + ": " + n);
            }
            return out;
        }

        @Override
        protected List<String> preview() {
            return Arrays.asList("포션: 12", "황금사과: 6", "엔더 진주: 3");
        }
    }

    /** 화면 위 가로 나침반 */
    static final class CompassBar extends Module {
        private static final String[] LABELS = {"남", "남서", "서", "북서", "북", "북동", "동", "남동"};
        private static final int W = 150;
        private static final int H = 14;

        CompassBar() {
            super("compassbar", "나침반", false, -5, -5);
            meta("HUD", "화면 위 가로 나침반", Icons.COMPASS);
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            float yaw = ((g.yaw() % 360f) + 360f) % 360f;
            Theme.panel(r, x, y, W, H);
            int cx = x + W / 2;
            int half = W / 2 - 6;
            r.scissor(x + 2, y, x + W - 2, y + H);
            for (int a = 0; a < 360; a += 15) {
                float d = a - yaw;
                while (d > 180) d -= 360;
                while (d < -180) d += 360;
                int px = Math.round(cx + d);
                if (Math.abs(d) > half) continue;
                if (a % 45 == 0) {
                    String s = LABELS[a / 45];
                    boolean main = a % 90 == 0;
                    int col = main ? (a == 180 ? Theme.accent(px, y) : Theme.WHITE) : Theme.MUTED;
                    r.centered(s, px, y + 3, col, cfg.shadow);
                } else {
                    r.fill(px, y + H - 4, px + 1, y + H - 1, 0x80FFFFFF);
                }
            }
            r.endScissor();
            r.fill(cx, y, cx + 1, y + 2, Theme.accent(cx, y));
            r.fill(cx, y + H - 2, cx + 1, y + H, Theme.accent(cx, y));
            lastW = W;
            lastH = H;
        }
    }

    /** 배고픔·포화도 */
    static final class Hunger extends Module.TextModule {
        Hunger() {
            super("hunger", "허기·포화", false, AUTO, AUTO);
            meta("HUD", "배고픔과 숨은 포화도", Icons.FOOD);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            return Collections.singletonList(String.format(Locale.ROOT, "허기: %d · 포화 %.1f", g.food(), g.saturation()));
        }
    }

    /** 자바 메모리 사용량 */
    static final class Memory extends Module.TextModule {
        Memory() {
            super("memory", "메모리", false, AUTO, AUTO);
            meta("성능", "게임이 쓰는 메모리", Icons.RAM);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            Runtime rt = Runtime.getRuntime();
            long used = (rt.totalMemory() - rt.freeMemory()) >> 20;
            long max = rt.maxMemory() >> 20;
            return Collections.singletonList("메모리: " + used + "/" + max + "MB (" + (max > 0 ? used * 100 / max : 0) + "%)");
        }
    }

    /** 접속한 서버 주소 */
    static final class Server extends Module.TextModule {
        Server() {
            super("server", "서버 주소", false, AUTO, AUTO);
            meta("HUD", "접속한 서버 주소", Icons.SERVER);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            String s = g.server();
            return Collections.singletonList("서버: " + (s == null || s.length() == 0 ? "싱글플레이" : s));
        }
    }

    /** 인벤토리 (가방 27칸, 선택하면 핫바 9칸까지) */
    static final class Inventory extends Module {
        private static final int SLOT = 18;

        Inventory() {
            super("inventory", "인벤토리", false, -6, -6); // -6 = 핫바 위 가운데
            meta("PvP", "가방 속 아이템을 화면에 표시", Icons.BAG);
        }

        @Override
        public List<Option> options() {
            return Arrays.asList(new Option("hotbar", "핫바 줄도 표시", false), new Option("slots", "빈 칸 테두리 표시", true));
        }

        @Override
        public void render(HudRenderer r, GameInfo g, HudConfig cfg, int x, int y, boolean editing) {
            boolean hotbar = opt("hotbar");
            boolean slots = opt("slots");
            int rows = hotbar ? 4 : 3;
            int pad = cfg.background && !Theme.minimal() ? 3 : 0;
            int w = 9 * SLOT + pad * 2;
            int h = rows * SLOT + (hotbar ? 3 : 0) + pad * 2;
            Theme.panel(r, x, y, w, h);
            for (int row = 0; row < rows; row++) {
                int ry = y + pad + row * SLOT + (row == 3 ? 3 : 0);
                for (int col = 0; col < 9; col++) {
                    int rx = x + pad + col * SLOT;
                    if (slots) r.fill(rx + 1, ry + 1, rx + SLOT - 1, ry + SLOT - 1, 0x30FFFFFF);
                }
            }
            // 아이템은 칸을 다 그린 뒤 (배경과 섞여 그려지지 않게)
            for (int row = 0; row < rows; row++) {
                int base = row < 3 ? 9 + row * 9 : 0; // 가방 9~35, 핫바 0~8
                int ry = y + pad + row * SLOT + (row == 3 ? 3 : 0);
                for (int col = 0; col < 9; col++) {
                    Object it = g.slot(base + col);
                    if (it != null) r.item(it, x + pad + col * SLOT + 1, ry + 1);
                }
            }
            lastW = w;
            lastH = h;
        }
    }

    /** 조준한 상대까지 거리 (3칸 안 초록, 밖 빨강) */
    static final class Aim extends Module.TextModule {
        Aim() {
            super("aim", "조준 거리", false, AUTO, AUTO);
            meta("PvP", "조준한 플레이어까지 거리 (3칸 안이면 초록)", Icons.TARGET);
        }

        @Override
        protected List<String> lines(GameInfo g) {
            double d = PvpAids.aim;
            return Collections.singletonList("조준: " + (d < 0 ? "-" : String.format(Locale.ROOT, "%.2f", d) + (d <= PvpAids.REACH ? " (닿음)" : "")));
        }

        @Override
        protected List<String> preview() {
            return Collections.singletonList("조준: 2.84 (닿음)");
        }

        @Override
        protected int valueColor(String line, HudConfig cfg) {
            return PvpAids.inReach() ? Crosshair.IN_REACH : cfg.textColor;
        }
    }
}
