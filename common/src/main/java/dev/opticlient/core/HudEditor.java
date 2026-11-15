package dev.opticlient.core;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Right Shift 로 여는 OptiClient 메뉴 (버전 공통).
 * 탭: 모드 메뉴(카드 격자) / HUD 편집(드래그) / 설정.
 * 각 버전의 Screen 은 그리기·마우스·키 입력만 여기로 넘겨준다.
 */
public final class HudEditor {
    private static final int TAB_MODS = 0;
    private static final int TAB_EDIT = 1;
    private static final int TAB_SETTINGS = 2;
    private static final String[] TAB_NAMES = {"모드 메뉴", "HUD 편집", "설정"};
    private static final String[] CATEGORIES = {"전체", "HUD", "PvP", "성능", "편의"};

    private static final int BAR_H = 20;
    private static final int CHIP_H = 14;
    private static final int CARD_MIN_W = 104;
    private static final int CARD_H = 76;
    private static final int GAP = 6;

    // ---------------- 카드 ----------------

    private interface Flag {
        boolean get();

        void set(boolean v);
    }

    /** 메뉴 카드 하나 (HUD 요소 또는 기능) */
    private static final class Card {
        final String id;
        final String name;
        final String category;
        final String desc;
        final String[] icon;
        final Flag flag;
        final int gearTab;

        Card(String id, String name, String category, String desc, String[] icon, Flag flag, int gearTab) {
            this.id = id;
            this.name = name;
            this.category = category;
            this.desc = desc;
            this.icon = icon;
            this.flag = flag;
            this.gearTab = gearTab;
        }
    }

    /** 설정 탭의 한 줄 */
    private abstract static class Row {
        final String label;

        Row(String label) {
            this.label = label;
        }

        Boolean on() {
            return null;
        }

        String value() {
            return null;
        }

        int labelColor() {
            return Theme.TEXT;
        }

        abstract void run();
    }

    private final List<Card> cards = new ArrayList<Card>();
    private final List<Row> rows = new ArrayList<Row>();

    // 상태
    private int tab = TAB_MODS;
    private int category = 0;
    private boolean favoritesOnly;
    private String search = "";
    private boolean searchFocused;
    private int scroll;
    private int maxScroll;
    private boolean closeRequested;
    private Module highlight;

    // HUD 편집 드래그
    private Module dragging;
    private int dragOffX;
    private int dragOffY;

    // 마지막으로 그린 화면 크기 (클릭 판정용)
    private int sw;
    private int sh;
    private final SimpleDateFormat clock = new SimpleDateFormat("yyyy/MM/dd  a h:mm", Locale.KOREAN);

    /** 클릭 판정에서 글자 폭을 잴 때 쓰는 마지막 렌더러 */
    private HudRenderer measure = new HudRenderer() {
        public void fill(int x1, int y1, int x2, int y2, int argb) { }
        public void text(String s, int x, int y, int argb, boolean shadow) { }
        public int width(String s) { return s.length() * 6; }
        public int lineHeight() { return 10; }
        public void item(Object stack, int x, int y) { }
        public void pushTransform(int x, int y, float scale) { }
        public void popTransform() { }
    };

    public HudEditor() {
        for (final Module m : OptiCore.MODULES) {
            cards.add(new Card(m.id, m.name, m.category, m.desc, m.icon, new Flag() {
                public boolean get() { return OptiCore.state(m).enabled; }
                public void set(boolean v) { OptiCore.state(m).enabled = v; }
            }, TAB_EDIT));
        }
        cards.add(new Card("boost", "FPS 부스트", "성능", "그래픽을 PvP·FPS 위주로", Icons.BOLT, new Flag() {
            public boolean get() { return OptiCore.config.pvpBoost; }
            public void set(boolean v) { OptiCore.config.pvpBoost = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("zoom", "줌", "편의", "C 키를 누르는 동안 확대", Icons.ZOOM, new Flag() {
            public boolean get() { return OptiCore.config.zoomEnabled; }
            public void set(boolean v) { OptiCore.config.zoomEnabled = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("fullbright", "밝기 최대", "편의", "동굴·밤에도 밝게", Icons.SUN, new Flag() {
            public boolean get() { return OptiCore.config.fullbright; }
            public void set(boolean v) { OptiCore.config.fullbright = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("togglesprint", "달리기 토글", "PvP", "V 키로 달리기 고정", Icons.RUN, new Flag() {
            public boolean get() { return OptiCore.config.toggleSprint; }
            public void set(boolean v) { OptiCore.config.toggleSprint = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("freelook", "퍼스펙티브", "PvP", "Left Alt: 몸은 두고 카메라만 돌리기", Icons.EYE, new Flag() {
            public boolean get() { return OptiCore.config.freelook; }
            public void set(boolean v) { OptiCore.config.freelook = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("togglesneak", "웅크리기 토글", "PvP", "G 키로 웅크리기 고정", Icons.SNEAK, new Flag() {
            public boolean get() { return OptiCore.config.toggleSneak; }
            public void set(boolean v) { OptiCore.config.toggleSneak = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("crosshair", "크로스헤어", "PvP", "모양·색·크기를 바꾼 조준점", Icons.CROSS, new Flag() {
            public boolean get() { return OptiCore.config.crosshair; }
            public void set(boolean v) { OptiCore.config.crosshair = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("nohurtcam", "흔들림 끄기", "PvP", "맞아도 화면이 흔들리지 않음", Icons.SHAKE, new Flag() {
            public boolean get() { return OptiCore.config.noHurtCam; }
            public void set(boolean v) { OptiCore.config.noHurtCam = v; }
        }, TAB_SETTINGS));
        cards.add(new Card("lowfire", "낮은 불", "PvP", "불 붙었을 때 화면 가림 줄이기", Icons.FIRE, new Flag() {
            public boolean get() { return OptiCore.config.lowFire; }
            public void set(boolean v) { OptiCore.config.lowFire = v; }
        }, TAB_SETTINGS));
        if (OptiCore.glintSupported) {
            cards.add(new Card("noglint", "반짝임 끄기", "성능", "인챈트 반짝임을 안 그려 FPS 향상", Icons.SPARK, new Flag() {
                public boolean get() { return OptiCore.config.noGlint; }
                public void set(boolean v) { OptiCore.config.noGlint = v; }
            }, TAB_SETTINGS));
        }
        cards.add(new Card("hitboxes", "히트박스", "PvP", "엔티티 충돌 상자 표시", Icons.BOX, new Flag() {
            public boolean get() { return OptiCore.config.hitboxes; }
            public void set(boolean v) { OptiCore.config.hitboxes = v; }
        }, TAB_SETTINGS));

        rows.add(new Row("HUD 스타일") {
            String value() { return Theme.styleName(); }
            void run() { Theme.nextStyle(); }
        });
        rows.add(new Row("강조색") {
            String value() { return Theme.ACCENT_NAMES[Theme.accentIndex()]; }
            void run() { Theme.nextAccent(); }
        });
        rows.add(toggleRow("HUD 배경", new Flag() {
            public boolean get() { return OptiCore.config.background; }
            public void set(boolean v) { OptiCore.config.background = v; }
        }));
        rows.add(toggleRow("글자 그림자", new Flag() {
            public boolean get() { return OptiCore.config.shadow; }
            public void set(boolean v) { OptiCore.config.shadow = v; }
        }));
        rows.add(new Row("줌 배율") {
            String value() { return (int) OptiCore.config.zoomFactor + "x"; }
            void run() {
                double z = OptiCore.config.zoomFactor;
                OptiCore.config.zoomFactor = z >= 8 ? 2 : z * 2;
            }
        });
        rows.add(new Row("크로스헤어 모양") {
            String value() { return Crosshair.STYLES[Math.max(0, Math.min(3, OptiCore.config.crosshairStyle))]; }
            void run() { OptiCore.config.crosshairStyle = (OptiCore.config.crosshairStyle + 1) % Crosshair.STYLES.length; }
        });
        rows.add(new Row("크로스헤어 색") {
            String value() { return Crosshair.COLORS[Math.max(0, Math.min(4, OptiCore.config.crosshairColor))]; }
            void run() { OptiCore.config.crosshairColor = (OptiCore.config.crosshairColor + 1) % Crosshair.COLORS.length; }
        });
        rows.add(new Row("크로스헤어 크기") {
            String value() { return Crosshair.SIZES[Math.max(0, Math.min(2, OptiCore.config.crosshairSize))]; }
            void run() { OptiCore.config.crosshairSize = (OptiCore.config.crosshairSize + 1) % Crosshair.SIZES.length; }
        });
        rows.add(new Row("퍼스펙티브 키") {
            String value() { return OptiCore.config.freelookToggle ? "토글" : "누르는 동안"; }
            void run() { OptiCore.config.freelookToggle = !OptiCore.config.freelookToggle; }
        });
        rows.add(toggleRow("FPS 제한 해제 (항상)", new Flag() {
            public boolean get() { return OptiCore.config.unlockFps; }
            public void set(boolean v) { OptiCore.config.unlockFps = v; }
        }));
        rows.add(toggleRow("OptiClient 타이틀 화면", new Flag() {
            public boolean get() { return OptiCore.config.customTitle; }
            public void set(boolean v) { OptiCore.config.customTitle = v; }
        }));
        rows.add(new Row("부스트 렌더 거리") {
            String value() { return OptiCore.config.boostRenderDistance + "청크"; }
            void run() {
                int d = OptiCore.config.boostRenderDistance;
                OptiCore.config.boostRenderDistance = d >= 12 ? 2 : d + 2;
            }
        });
        rows.add(new Row("HUD 위치 초기화") {
            int labelColor() { return 0xFFFF7A7A; }
            void run() { OptiCore.resetPositions(); }
        });
    }

    private static Row toggleRow(String label, final Flag f) {
        return new Row(label) {
            Boolean on() { return f.get(); }
            void run() { f.set(!f.get()); }
        };
    }

    // =====================================================================
    // 그리기
    // =====================================================================

    public void render(HudRenderer r, GameInfo g, int mx, int my, int screenW, int screenH) {
        sw = screenW;
        sh = screenH;
        measure = r;
        if (tab == TAB_EDIT) {
            renderEdit(r, g, mx, my);
            return;
        }
        r.fill(0, 0, sw, sh, 0xA0000000);
        renderTopBar(r, mx, my);
        int[] p = panelRect();
        Theme.round(r, p[0], p[1], p[2], p[3], Theme.MENU_BG, 4);
        if (tab == TAB_MODS) renderMods(r, mx, my, p);
        else renderSettings(r, mx, my, p);
        r.flush();
    }

    private int margin() {
        return sw >= 560 ? 18 : 6;
    }

    /** 패널 영역 {x1, y1, x2, y2} */
    private int[] panelRect() {
        int m = margin();
        return new int[]{m, m + BAR_H + 6, sw - m, sh - m};
    }

    // ---------- 상단 바 ----------

    /** 상단 바 왼쪽 로고 자리 폭 */
    private static final int LOGO_W = 22;

    private int[] tabRect(int i) {
        int x = margin() + LOGO_W;
        for (int k = 0; k < i; k++) x += tabWidth(k) + 4;
        return new int[]{x, margin(), x + tabWidth(i), margin() + BAR_H};
    }

    private int tabWidth(int i) {
        return measure.width(TAB_NAMES[i]) + (i == 0 ? 30 : 18);
    }

    private int[] closeRect() {
        int m = margin();
        return new int[]{sw - m - BAR_H, m, sw - m, m + BAR_H};
    }

    private void renderTopBar(HudRenderer r, int mx, int my) {
        Logo.draw(r, margin() + 1, margin() + 2, 1, 255);
        for (int i = 0; i < TAB_NAMES.length; i++) {
            int[] t = tabRect(i);
            boolean sel = i == tab;
            int bg = sel ? Theme.accent(t[0], t[1]) : inside(mx, my, t) ? Theme.CHIP_HOVER : Theme.CHIP_BG;
            Theme.round(r, t[0], t[1], t[2], t[3], bg, 3);
            int tx = t[0] + 9;
            if (i == 0) {
                Icons.draw(r, Icons.GRID, t[0] + 8, t[1] + 7, 1, Theme.WHITE);
                tx = t[0] + 20;
            }
            r.text(TAB_NAMES[i], tx, t[1] + 6, sel ? Theme.WHITE : Theme.TEXT, false);
        }
        // 날짜·시각 칩 (자리가 있을 때만)
        int[] c = closeRect();
        String time = clock.format(new Date());
        int tw = r.width(time) + 22;
        int lastTab = tabRect(TAB_NAMES.length - 1)[2];
        if (c[0] - 6 - tw > lastTab + 8) {
            int x = c[0] - 6 - tw;
            Theme.round(r, x, c[1], x + tw, c[3], Theme.CHIP_BG, 3);
            Theme.round(r, x + 7, c[1] + 7, x + 13, c[1] + 13, Theme.accent(x, c[1]), 2);
            r.text(time, x + 16, c[1] + 6, Theme.TEXT, false);
        }
        // 닫기 버튼
        int red = Theme.accent(c[0], c[1]);
        if (inside(mx, my, c)) Theme.round(r, c[0], c[1], c[2], c[3], Theme.withAlpha(red, 0x40), 3);
        Theme.roundOutline(r, c[0], c[1], c[2], c[3], red, 3);
        r.centered("X", (c[0] + c[2]) / 2 + 1, c[1] + 6, red, false);
    }

    // ---------- 모드 메뉴 ----------

    private int[] chipRect(int[] p, int i) {
        int x = p[0] + 8;
        for (int k = 0; k < i; k++) x += chipWidth(k) + 4;
        return new int[]{x, p[1] + 8, x + chipWidth(i), p[1] + 8 + CHIP_H};
    }

    private int chipWidth(int i) {
        return Math.max(34, measure.width(CATEGORIES[i]) + 16);
    }

    private int[] heartRect(int[] p) {
        return new int[]{p[2] - 8 - CHIP_H, p[1] + 8, p[2] - 8, p[1] + 8 + CHIP_H};
    }

    private int[] searchRect(int[] p) {
        int[] h = heartRect(p);
        int chipsEnd = chipRect(p, CATEGORIES.length - 1)[2];
        int w = Math.max(50, Math.min(120, h[0] - 4 - chipsEnd - 8));
        return new int[]{h[0] - 4 - w, h[1], h[0] - 4, h[3]};
    }

    private int[] gridRect(int[] p) {
        return new int[]{p[0] + 8, p[1] + 8 + CHIP_H + 8, p[2] - 8, p[3] - 8};
    }

    private List<Card> visibleCards() {
        List<Card> out = new ArrayList<Card>();
        String q = search.trim().toLowerCase(Locale.ROOT);
        for (Card c : cards) {
            if (category > 0 && !CATEGORIES[category].equals(c.category)) continue;
            if (favoritesOnly && !OptiCore.config.favorites.contains(c.id)) continue;
            if (q.length() > 0 && !c.name.toLowerCase(Locale.ROOT).contains(q) && !c.desc.toLowerCase(Locale.ROOT).contains(q)) continue;
            out.add(c);
        }
        // 즐겨찾기를 앞으로
        List<Card> sorted = new ArrayList<Card>();
        for (Card c : out) if (OptiCore.config.favorites.contains(c.id)) sorted.add(c);
        for (Card c : out) if (!OptiCore.config.favorites.contains(c.id)) sorted.add(c);
        return sorted;
    }

    private int columns(int gridW) {
        return Math.max(1, (gridW + GAP) / (CARD_MIN_W + GAP));
    }

    /** 카드 i 의 화면 위치 {x1, y1, x2, y2} (스크롤 반영) */
    private int[] cardRect(int[] grid, int i) {
        int gw = grid[2] - grid[0];
        int cols = columns(gw);
        int cw = (gw - 4 - GAP * (cols - 1)) / cols; // 4 = 스크롤바 자리
        int x = grid[0] + (i % cols) * (cw + GAP);
        int y = grid[1] + (i / cols) * (CARD_H + GAP) - scroll;
        return new int[]{x, y, x + cw, y + CARD_H};
    }

    private void renderMods(HudRenderer r, int mx, int my, int[] p) {
        // 카테고리 칩
        for (int i = 0; i < CATEGORIES.length; i++) {
            int[] c = chipRect(p, i);
            boolean sel = i == category;
            int bg = sel ? Theme.accent(c[0], c[1]) : inside(mx, my, c) ? Theme.CHIP_HOVER : Theme.CHIP_BG;
            Theme.round(r, c[0], c[1], c[2], c[3], bg, 3);
            r.centered(CATEGORIES[i], (c[0] + c[2]) / 2, c[1] + 3, sel ? Theme.WHITE : Theme.MUTED, false);
        }
        // 검색창
        int[] s = searchRect(p);
        Theme.round(r, s[0], s[1], s[2], s[3], searchFocused ? Theme.CHIP_HOVER : Theme.CHIP_BG, 3);
        if (searchFocused) Theme.roundOutline(r, s[0], s[1], s[2], s[3], Theme.accent(s[0], s[1]), 3);
        boolean empty = search.length() == 0;
        boolean blink = searchFocused && (System.currentTimeMillis() / 500) % 2 == 0;
        String shown = empty && !searchFocused ? "검색" : search + (blink ? "_" : "");
        r.scissor(s[0] + 2, s[1], s[2] - 2, s[3]);
        r.text(shown, s[0] + 6, s[1] + 3, empty && !searchFocused ? Theme.MUTED : Theme.TEXT, false);
        r.endScissor();
        // 즐겨찾기 필터
        int[] h = heartRect(p);
        Theme.round(r, h[0], h[1], h[2], h[3], favoritesOnly ? Theme.withAlpha(Theme.accent(), 0x50) : Theme.CHIP_BG, 3);
        Icons.drawCentered(r, Icons.HEART, (h[0] + h[2]) / 2, (h[1] + h[3]) / 2, 1, favoritesOnly ? Theme.accent(h[0], h[1]) : Theme.MUTED);

        // 카드 격자
        int[] grid = gridRect(p);
        List<Card> list = visibleCards();
        int cols = columns(grid[2] - grid[0]);
        int rowsCount = (list.size() + cols - 1) / cols;
        int contentH = Math.max(0, rowsCount * (CARD_H + GAP) - GAP);
        maxScroll = Math.max(0, contentH - (grid[3] - grid[1]));
        scroll = Math.max(0, Math.min(scroll, maxScroll));
        boolean mouseInGrid = inside(mx, my, grid);

        r.scissor(grid[0], grid[1], grid[2], grid[3]);
        for (int i = 0; i < list.size(); i++) {
            int[] c = cardRect(grid, i);
            if (c[3] < grid[1] || c[1] > grid[3]) continue;
            renderCard(r, list.get(i), c, mx, my, mouseInGrid);
        }
        if (list.isEmpty()) r.centered("찾는 모드가 없어요", (grid[0] + grid[2]) / 2, grid[1] + 30, Theme.MUTED, false);
        r.endScissor();

        // 스크롤바
        if (maxScroll > 0) {
            int trackH = grid[3] - grid[1];
            int thumbH = Math.max(16, trackH * trackH / Math.max(1, contentH));
            int ty = grid[1] + (trackH - thumbH) * scroll / maxScroll;
            Theme.round(r, grid[2] - 3, grid[1], grid[2], grid[3], 0x20FFFFFF, 1);
            Theme.round(r, grid[2] - 3, ty, grid[2], ty + thumbH, 0x90FFFFFF, 1);
        }
    }

    private int[] cardHeart(int[] c) {
        return new int[]{c[2] - 17, c[1] + 5, c[2] - 5, c[1] + 17};
    }

    private int[] cardGear(int[] c) {
        return new int[]{c[0] + 6, c[3] - 20, c[0] + 20, c[3] - 6};
    }

    private int[] cardToggle(int[] c) {
        return new int[]{c[0] + 24, c[3] - 20, c[2] - 6, c[3] - 6};
    }

    private void renderCard(HudRenderer r, Card card, int[] c, int mx, int my, boolean mouseInGrid) {
        boolean on = card.flag.get();
        boolean hover = mouseInGrid && inside(mx, my, c);
        Theme.round(r, c[0], c[1], c[2], c[3], hover ? Theme.CARD_HOVER : Theme.CARD_BG, 4);
        if (on) r.fill(c[0] + 5, c[1], c[0] + 19, c[1] + 1, Theme.accent(c[0], c[1]));

        // 제목 + 하트
        r.text(card.name, c[0] + 7, c[1] + 7, on ? Theme.WHITE : Theme.MUTED, false);
        int[] h = cardHeart(c);
        boolean fav = OptiCore.config.favorites.contains(card.id);
        Theme.round(r, h[0], h[1], h[2], h[3], mouseInGrid && inside(mx, my, h) ? Theme.CHIP_HOVER : Theme.CHIP_BG, 3);
        Icons.drawCentered(r, Icons.HEART, (h[0] + h[2]) / 2, (h[1] + h[3]) / 2 + 1, 1, fav ? Theme.accent(h[0], h[1]) : Theme.DIM);

        // 아이콘 (마우스를 올리면 설명)
        if (hover && !inside(mx, my, cardToggle(c)) && !inside(mx, my, cardGear(c)) && !inside(mx, my, h)) {
            r.centered(card.desc, (c[0] + c[2]) / 2, c[1] + 32, Theme.MUTED, false);
        } else {
            Icons.drawCentered(r, card.icon, (c[0] + c[2]) / 2, c[1] + 36, 3, on ? Theme.WHITE : Theme.DIM);
        }

        // 톱니 + 켜짐/꺼짐
        int[] gear = cardGear(c);
        Theme.round(r, gear[0], gear[1], gear[2], gear[3], mouseInGrid && inside(mx, my, gear) ? Theme.CHIP_HOVER : Theme.CHIP_BG, 3);
        Icons.drawCentered(r, Icons.GEAR, (gear[0] + gear[2]) / 2, (gear[1] + gear[3]) / 2, 1, Theme.MUTED);
        int[] t = cardToggle(c);
        boolean th = mouseInGrid && inside(mx, my, t);
        int red = Theme.accent(t[0], t[1]);
        int bg = on ? (th ? Theme.lerp(red, Theme.WHITE, 0.12f) : red) : (th ? Theme.CHIP_HOVER : Theme.OFF_BG);
        Theme.round(r, t[0], t[1], t[2], t[3], bg, 3);
        r.centered(on ? "켜짐" : "꺼짐", (t[0] + t[2]) / 2, t[1] + 3, on ? Theme.WHITE : Theme.MUTED, false);
    }

    // ---------- 설정 ----------

    private int[] rowRect(int[] p, int i) {
        int top = p[1] + 28;
        int avail = p[2] - p[0] - 20;
        boolean two = (p[3] - top - 20) / 18 < rows.size() || avail > 420;
        int colW = two ? Math.min(220, (avail - 8) / 2) : Math.min(300, avail);
        int col = two ? i % 2 : 0;
        int idx = two ? i / 2 : i;
        int x = p[0] + 10 + col * (colW + 8);
        int y = top + idx * 18;
        return new int[]{x, y, x + colW, y + 16};
    }

    private void renderSettings(HudRenderer r, int mx, int my, int[] p) {
        r.text("설정", p[0] + 10, p[1] + 10, Theme.WHITE, false);
        r.fill(p[0] + 10, p[1] + 20, p[0] + 10 + r.width("설정"), p[1] + 21, Theme.accent(p[0], p[1]));
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            int[] b = rowRect(p, i);
            Theme.round(r, b[0], b[1], b[2], b[3], inside(mx, my, b) ? Theme.CARD_HOVER : Theme.CARD_BG, 3);
            r.text(row.label, b[0] + 7, b[1] + 4, row.labelColor(), false);
            Boolean on = row.on();
            if (on != null) {
                int tx = b[2] - 24, ty = b[1] + 4;
                Theme.round(r, tx, ty, tx + 18, ty + 8, on ? Theme.accent(tx, ty) : Theme.OFF_BG, 4);
                int kx = on ? tx + 11 : tx + 1;
                Theme.round(r, kx, ty + 1, kx + 6, ty + 7, Theme.WHITE, 2);
            } else if (row.value() != null) {
                String v = row.value();
                int vw = r.width(v) + 12;
                int vx = b[2] - 6 - vw;
                Theme.round(r, vx, b[1] + 2, vx + vw, b[3] - 2, Theme.CHIP_BG, 3);
                if (row.label.equals("강조색")) Theme.round(r, vx - 9, b[1] + 5, vx - 3, b[1] + 11, Theme.accent(vx, b[1]), 2);
                r.text(v, vx + 6, b[1] + 4, Theme.accent(vx, b[1]), false);
            }
        }
        r.text("메뉴 RShift (1.8.9 RCtrl) · C 줌 · V 달리기 · G 웅크리기 · Alt 퍼스펙티브", p[0] + 10, p[3] - 14, Theme.DIM, false);
    }

    // ---------- HUD 편집 ----------

    private int[] editBar() {
        int w = measure.width("HUD 편집 · 요소를 드래그해서 옮기세요") + 112;
        int x = (sw - w) / 2;
        return new int[]{x, 6, x + w, 26};
    }

    /** 0 = 완료, 1 = 초기화 */
    private int[] editButton(int[] bar, int which) {
        int x2 = which == 0 ? bar[2] - 4 : bar[2] - 52;
        return new int[]{x2 - 44, bar[1] + 3, x2, bar[3] - 3};
    }

    private Module selected;

    /** 선택한 요소 위에 뜨는 도구 막대 {x1, y1, x2, y2} */
    private int[] toolbarRect(int[] p, Module m) {
        int w = 150, h = 16;
        int x = Math.max(2, Math.min(p[0], sw - w - 2));
        int y = p[1] - h - 5 >= 30 ? p[1] - h - 5 : p[1] + m.boxH + 5;
        return new int[]{x, y, x + w, y + h};
    }

    /** 0 = 작게, 1 = 크기 표시(누르면 100%), 2 = 크게, 3 = 삭제 */
    private int[] toolButton(int[] t, int i) {
        int[][] x = {{2, 18}, {20, 62}, {64, 80}, {84, 148}};
        return new int[]{t[0] + x[i][0], t[1] + 2, t[0] + x[i][1], t[3] - 2};
    }

    /** 마우스를 올린 요소 모서리의 삭제(×) 버튼 */
    private int[] closeBadge(int[] p, Module m) {
        int x = p[0] + m.boxW - 4, y = p[1] - 5;
        return new int[]{x, y, x + 9, y + 9};
    }

    private void renderEdit(HudRenderer r, GameInfo g, int mx, int my) {
        r.fill(0, 0, sw, sh, 0x50000000);
        for (int x = 0; x < sw; x += 20) r.fill(x, 0, x + 1, sh, 0x0CFFFFFF);
        for (int y = 0; y < sh; y += 20) r.fill(0, y, sw, y + 1, 0x0CFFFFFF);

        Module hovered = null;
        for (Module m : OptiCore.MODULES) {
            HudConfig.ModuleState s = OptiCore.state(m);
            if (!s.enabled) continue;
            int[] p = OptiCore.resolve(m, s, sw, sh);
            OptiCore.renderModule(m, s, r, g, p[0], p[1], true);
            boolean hover = inside(mx, my, p[0], p[1], m.boxW, m.boxH);
            if (hover) hovered = m;
            boolean active = m == dragging || m == highlight || m == selected || hover;
            Theme.roundOutline(r, p[0] - 2, p[1] - 2, p[0] + m.boxW + 2, p[1] + m.boxH + 2,
                    active ? Theme.accent(p[0], p[1]) : 0x50FFFFFF, 3);
        }

        r.nextLayer();
        // 마우스를 올린 요소: 모서리 × (바로 삭제)
        if (hovered != null && dragging == null) {
            int[] p = OptiCore.resolve(hovered, OptiCore.state(hovered), sw, sh);
            int[] b = closeBadge(p, hovered);
            Theme.round(r, b[0], b[1], b[2], b[3], inside(mx, my, b) ? 0xFFFF5A5F : 0xFFE5383B, 4);
            r.fill(b[0] + 2, b[1] + 4, b[2] - 2, b[1] + 5, Theme.WHITE);
        }
        // 선택한 요소: 크기 도구 막대
        if (selected != null && OptiCore.state(selected).enabled && dragging == null) {
            HudConfig.ModuleState s = OptiCore.state(selected);
            int[] p = OptiCore.resolve(selected, s, sw, sh);
            int[] t = toolbarRect(p, selected);
            Theme.round(r, t[0], t[1], t[2], t[3], Theme.MENU_BG, 4);
            String[] labels = {"－", Math.round(OptiCore.scaleOf(s) * 100) + "%", "＋", "삭제"};
            for (int i = 0; i < 4; i++) {
                int[] b = toolButton(t, i);
                boolean hv = inside(mx, my, b);
                int bg = i == 3 ? (hv ? 0xFFFF5A5F : 0xFFE5383B) : (hv ? Theme.CHIP_HOVER : Theme.CHIP_BG);
                Theme.round(r, b[0], b[1], b[2], b[3], bg, 2);
                r.centered(labels[i], (b[0] + b[2]) / 2, b[1] + 2, Theme.WHITE, false);
            }
        }

        int[] bar = editBar();
        Theme.round(r, bar[0], bar[1], bar[2], bar[3], Theme.MENU_BG, 4);
        r.text("HUD 편집 · 요소를 드래그해서 옮기세요", bar[0] + 8, bar[1] + 6, Theme.TEXT, false);
        int[] done = editButton(bar, 0);
        Theme.round(r, done[0], done[1], done[2], done[3], Theme.accent(done[0], done[1]), 3);
        r.centered("완료", (done[0] + done[2]) / 2, done[1] + 4, Theme.WHITE, false);
        int[] reset = editButton(bar, 1);
        Theme.round(r, reset[0], reset[1], reset[2], reset[3], inside(mx, my, reset) ? Theme.CHIP_HOVER : Theme.CHIP_BG, 3);
        r.centered("초기화", (reset[0] + reset[2]) / 2, reset[1] + 4, Theme.TEXT, false);
        r.centered("클릭: 선택 · 휠: 크기 · 오른쪽 클릭 / × : 삭제", sw / 2, sh - 12, 0xFFBBBBBB, true);
        r.flush();
    }

    private void changeScale(Module m, float delta) {
        HudConfig.ModuleState s = OptiCore.state(m);
        float v = Math.round((OptiCore.scaleOf(s) + delta) * 10f) / 10f;
        s.scale = Math.max(0.5f, Math.min(2f, v));
        OptiCore.save();
    }

    private void deleteModule(Module m) {
        OptiCore.state(m).enabled = false;
        if (selected == m) selected = null;
        OptiCore.save();
    }

    private Module moduleAt(int mx, int my) {
        for (int i = OptiCore.MODULES.size() - 1; i >= 0; i--) {
            Module m = OptiCore.MODULES.get(i);
            HudConfig.ModuleState s = OptiCore.state(m);
            if (!s.enabled) continue;
            int[] p = OptiCore.resolve(m, s, sw, sh);
            if (inside(mx, my, p[0], p[1], m.boxW, m.boxH)) return m;
        }
        return null;
    }

    // =====================================================================
    // 입력
    // =====================================================================

    /** 자동 테스트용: 탭 바꾸기 (0 모드 메뉴, 1 HUD 편집, 2 설정) */
    public void selectTab(int t) {
        tab = t;
    }

    /** 자동 테스트용: HUD 편집에서 요소를 고르고 크기를 바꾼다 */
    public void selectForTest(String id, float scale) {
        for (Module m : OptiCore.MODULES) {
            if (m.id.equals(id)) {
                selected = m;
                OptiCore.state(m).scale = scale;
            }
        }
    }

    /** 메뉴를 닫아야 하는지 (닫기 버튼) — 각 버전 Screen 이 확인한다 */
    public boolean shouldClose() {
        return closeRequested;
    }

    public void mousePressed(int mx, int my, int button) {
        if (tab == TAB_EDIT) {
            if (button == 1) { // 오른쪽 클릭: 바로 삭제
                Module m = moduleAt(mx, my);
                if (m != null) deleteModule(m);
                return;
            }
            if (button == 0) pressEdit(mx, my);
            return;
        }
        if (button != 0) return;
        if (inside(mx, my, closeRect())) {
            closeRequested = true;
            return;
        }
        for (int i = 0; i < TAB_NAMES.length; i++) {
            if (inside(mx, my, tabRect(i))) {
                tab = i;
                highlight = null;
                searchFocused = false;
                return;
            }
        }
        int[] p = panelRect();
        if (tab == TAB_SETTINGS) {
            for (int i = 0; i < rows.size(); i++) {
                if (inside(mx, my, rowRect(p, i))) {
                    rows.get(i).run();
                    OptiCore.save();
                    return;
                }
            }
            return;
        }
        // 모드 메뉴
        searchFocused = inside(mx, my, searchRect(p));
        if (searchFocused) return;
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (inside(mx, my, chipRect(p, i))) {
                category = i;
                scroll = 0;
                return;
            }
        }
        if (inside(mx, my, heartRect(p))) {
            favoritesOnly = !favoritesOnly;
            scroll = 0;
            return;
        }
        int[] grid = gridRect(p);
        if (!inside(mx, my, grid)) return;
        List<Card> list = visibleCards();
        for (int i = 0; i < list.size(); i++) {
            int[] c = cardRect(grid, i);
            if (!inside(mx, my, c)) continue;
            Card card = list.get(i);
            if (inside(mx, my, cardHeart(c))) {
                if (!OptiCore.config.favorites.remove(card.id)) OptiCore.config.favorites.add(card.id);
            } else if (inside(mx, my, cardGear(c))) {
                tab = card.gearTab;
                highlight = null;
                for (Module m : OptiCore.MODULES) if (m.id.equals(card.id)) highlight = m;
                if (highlight != null) OptiCore.state(highlight).enabled = true;
            } else {
                card.flag.set(!card.flag.get()); // 카드 어디를 눌러도 켜고 끈다
            }
            OptiCore.save();
            return;
        }
    }

    private void pressEdit(int mx, int my) {
        int[] bar = editBar();
        if (inside(mx, my, editButton(bar, 0))) {
            tab = TAB_MODS;
            highlight = null;
            selected = null;
            OptiCore.save();
            return;
        }
        if (inside(mx, my, editButton(bar, 1))) {
            OptiCore.resetPositions();
            return;
        }
        if (inside(mx, my, bar)) return;

        // 선택한 요소의 도구 막대
        if (selected != null && OptiCore.state(selected).enabled) {
            int[] t = toolbarRect(OptiCore.resolve(selected, OptiCore.state(selected), sw, sh), selected);
            if (inside(mx, my, t)) {
                if (inside(mx, my, toolButton(t, 0))) changeScale(selected, -0.1f);
                else if (inside(mx, my, toolButton(t, 1))) { OptiCore.state(selected).scale = 1f; OptiCore.save(); }
                else if (inside(mx, my, toolButton(t, 2))) changeScale(selected, 0.1f);
                else if (inside(mx, my, toolButton(t, 3))) deleteModule(selected);
                return;
            }
        }
        // 모서리 × (바로 삭제)
        Module hovered = moduleAt(mx, my);
        for (Module m : OptiCore.MODULES) {
            HudConfig.ModuleState s = OptiCore.state(m);
            if (!s.enabled) continue;
            if (inside(mx, my, closeBadge(OptiCore.resolve(m, s, sw, sh), m)) && (m == hovered || hovered == null)) {
                deleteModule(m);
                return;
            }
        }
        if (hovered != null) {
            int[] p = OptiCore.resolve(hovered, OptiCore.state(hovered), sw, sh);
            selected = hovered;
            dragging = hovered;
            dragOffX = mx - p[0];
            dragOffY = my - p[1];
        } else {
            selected = null;
        }
    }

    public void mouseDragged(int mx, int my) {
        if (tab != TAB_EDIT || dragging == null) return;
        HudConfig.ModuleState s = OptiCore.state(dragging);
        s.anchor = null; // 직접 옮기면 절대 좌표로
        s.x = Math.max(0, Math.min(mx - dragOffX, sw - dragging.boxW));
        s.y = Math.max(0, Math.min(my - dragOffY, sh - dragging.boxH));
    }

    public void mouseReleased() {
        if (dragging != null) OptiCore.save();
        dragging = null;
    }

    /** amount > 0 이면 위로. HUD 편집에서는 마우스 아래 요소의 크기를 바꾼다 */
    public void mouseScrolled(int mx, int my, double amount) {
        if (tab == TAB_EDIT) {
            Module m = moduleAt(mx, my);
            if (m != null) {
                selected = m;
                changeScale(m, amount > 0 ? 0.1f : -0.1f);
            }
            return;
        }
        if (tab != TAB_MODS) return;
        scroll -= (int) Math.round(amount * 24);
        scroll = Math.max(0, Math.min(scroll, maxScroll));
    }

    /** 검색창에 글자 입력. 입력을 받았으면 true */
    public boolean charTyped(char c) {
        if (!searchFocused || c < 32 || c == 127) return false;
        if (search.length() < 24) search += c;
        scroll = 0;
        return true;
    }

    /** 검색창 지우기. 입력을 받았으면 true */
    public boolean backspace() {
        if (!searchFocused) return false;
        if (search.length() > 0) search = search.substring(0, search.length() - 1);
        return true;
    }

    /** ESC: 검색 중이면 검색만 끝내고, HUD 편집 중이면 메뉴로 돌아간다. 메뉴를 닫아도 되면 true */
    public boolean escape() {
        if (searchFocused) {
            searchFocused = false;
            return false;
        }
        if (tab == TAB_EDIT) {
            tab = TAB_MODS;
            highlight = null;
            selected = null;
            OptiCore.save();
            return false;
        }
        return true;
    }

    public void close() {
        mouseReleased();
        OptiCore.save();
    }

    private static boolean inside(int mx, int my, int[] r) {
        return mx >= r[0] && my >= r[1] && mx < r[2] && my < r[3];
    }

    private static boolean inside(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }
}
