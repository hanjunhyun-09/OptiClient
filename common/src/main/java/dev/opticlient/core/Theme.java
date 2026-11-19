package dev.opticlient.core;

/**
 * HUD 스타일(모던/클래식/미니멀)과 강조색, 공통 그리기 도우미.
 * 기본 강조색은 빨강.
 */
public final class Theme {
    public static final String MODERN = "modern";
    public static final String CLASSIC = "classic";
    public static final String MINIMAL = "minimal";
    private static final String[] STYLES = {MODERN, CLASSIC, MINIMAL};
    private static final String[] STYLE_NAMES = {"모던", "클래식", "미니멀"};

    public static final String[] ACCENT_NAMES = {"빨강", "주황", "핑크", "보라", "하늘", "민트", "무지개"};
    private static final int[] ACCENTS = {0xFFE5383B, 0xFFFF8A3D, 0xFFFF6FAE, 0xFFA78BFA, 0xFF5AC8FA, 0xFF4CD9A0};
    public static final int RAINBOW = 6;

    public static final int WHITE = 0xFFFFFFFF;
    /** 모듈별 글자색 (0 = 흰색, 1 = 강조색 은 그릴 때 정함) */
    public static final String[] TEXT_COLOR_NAMES = {"흰색", "강조색", "초록", "노랑", "하늘", "분홍", "회색"};
    private static final int[] TEXT_COLORS = {0xFFFFFFFF, 0, 0xFF6EE7A0, 0xFFFFD166, 0xFF7CC8FF, 0xFFFF8FB8, 0xFFB8BCC8};
    /** 지금 그리는 모듈이 이름 글자를 숨기는지 (OptiCore.renderModule 이 정함) */
    static boolean hideLabel;

    public static int textColor(int idx) {
        if (idx <= 0 || idx >= TEXT_COLORS.length) return WHITE;
        return idx == 1 ? accent() : TEXT_COLORS[idx];
    }
    public static final int TEXT = 0xFFEDEEF2;
    public static final int MUTED = 0xFF8E929E;
    public static final int DIM = 0xFF5D616C;

    /** 인게임 HUD 배경 (어두운 유리 느낌) */
    public static final int HUD_BG = 0xB0101014;
    public static final int CLASSIC_BG = 0x70000000;

    /** 메뉴 색 */
    public static final int MENU_BG = 0xF2151619;
    public static final int CARD_BG = 0xFF1D1F24;
    public static final int CARD_HOVER = 0xFF24262C;
    public static final int CHIP_BG = 0xFF26282E;
    public static final int CHIP_HOVER = 0xFF2F3239;
    public static final int OFF_BG = 0xFF2A2C32;

    private Theme() {
    }

    // ---------- 스타일 ----------

    public static String style() {
        String s = OptiCore.config.style;
        return s == null ? MODERN : s;
    }

    public static boolean modern() {
        return MODERN.equals(style());
    }

    public static boolean minimal() {
        return MINIMAL.equals(style());
    }

    public static String styleName() {
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(style())) return STYLE_NAMES[i];
        return STYLE_NAMES[0];
    }

    public static void nextStyle() {
        int idx = 0;
        for (int i = 0; i < STYLES.length; i++) if (STYLES[i].equals(style())) idx = i;
        OptiCore.config.style = STYLES[(idx + 1) % STYLES.length];
    }

    // ---------- 강조색 ----------

    public static int accentIndex() {
        int a = OptiCore.config.accent;
        return a < 0 || a > RAINBOW ? 0 : a;
    }

    public static void nextAccent() {
        OptiCore.config.accent = (accentIndex() + 1) % ACCENT_NAMES.length;
    }

    /** 위치(x, y)에서의 강조색. 무지개면 시간과 위치에 따라 색이 흐른다. */
    public static int accent(int x, int y) {
        int a = accentIndex();
        if (a != RAINBOW) return ACCENTS[a];
        float hue = ((System.currentTimeMillis() % 4000L) / 4000f + (x + y) / 300f) % 1f;
        return 0xFF000000 | hsv(hue, 0.6f, 1f);
    }

    public static int accent() {
        return accent(0, 0);
    }

    /** 강조색을 어둡게 (버튼 눌림/테두리용) */
    public static int accentDark(int x, int y) {
        return lerp(accent(x, y), 0xFF000000, 0.35f);
    }

    /** HUD 라벨 색 (모던·미니멀·클래식 모두 강조색) */
    public static int label(int x, int y) {
        return accent(x, y);
    }

    public static int withAlpha(int argb, int alpha) {
        return (alpha << 24) | (argb & 0x00FFFFFF);
    }

    /** a → b 로 t(0~1) 만큼 섞기 */
    public static int lerp(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return ((int) (aa + (ba - aa) * t) << 24) | ((int) (ar + (br - ar) * t) << 16)
                | ((int) (ag + (bg - ag) * t) << 8) | (int) (ab + (bb - ab) * t);
    }

    private static int hsv(float h, float s, float v) {
        int i = (int) (h * 6f) % 6;
        float f = h * 6f - (int) (h * 6f);
        float p = v * (1 - s), q = v * (1 - f * s), t = v * (1 - (1 - f) * s);
        float r, g, b;
        switch (i) {
            case 0: r = v; g = t; b = p; break;
            case 1: r = q; g = v; b = p; break;
            case 2: r = p; g = v; b = t; break;
            case 3: r = p; g = q; b = v; break;
            case 4: r = t; g = p; b = v; break;
            default: r = v; g = p; b = q; break;
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    // ---------- 그리기 ----------

    /** 둥근 사각형. 줄마다 한 번씩만 칠해서 반투명이어도 겹친 부분이 진해지지 않는다. */
    public static void round(HudRenderer r, int x1, int y1, int x2, int y2, int argb, int radius) {
        int w = x2 - x1, h = y2 - y1;
        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (radius == 0) {
            r.fill(x1, y1, x2, y2, argb);
            return;
        }
        // 위·아래 모서리 줄 (반지름이 클수록 안쪽으로).
        // 안쪽 들여쓰기가 같은 줄끼리 한 번에 칠하고, 들여쓰기 0 인 줄은 가운데 사각형에 합친다
        // (반지름 3 이면 7번 → 3번 칠하기: HUD 그리기 비용 감소)
        int flat = radius; // 이 줄부터 들여쓰기 0
        while (flat > 0 && cornerInset(radius, flat - 1) == 0) flat--;
        int i = 0;
        while (i < flat) {
            int inset = cornerInset(radius, i);
            int j = i + 1;
            while (j < flat && cornerInset(radius, j) == inset) j++;
            r.fill(x1 + inset, y1 + i, x2 - inset, y1 + j, argb);
            r.fill(x1 + inset, y2 - j, x2 - inset, y2 - i, argb);
            i = j;
        }
        r.fill(x1, y1 + flat, x2, y2 - flat, argb);
    }

    public static void round(HudRenderer r, int x1, int y1, int x2, int y2, int argb) {
        round(r, x1, y1, x2, y2, argb, 1);
    }

    private static int cornerInset(int radius, int row) {
        double dy = radius - row - 0.5;
        double dx = Math.sqrt(Math.max(0, radius * radius - dy * dy));
        return (int) Math.round(radius - dx);
    }

    /** 둥근 테두리 (1px) */
    public static void roundOutline(HudRenderer r, int x1, int y1, int x2, int y2, int argb, int radius) {
        r.fill(x1 + radius, y1, x2 - radius, y1 + 1, argb);
        r.fill(x1 + radius, y2 - 1, x2 - radius, y2, argb);
        r.fill(x1, y1 + radius, x1 + 1, y2 - radius, argb);
        r.fill(x2 - 1, y1 + radius, x2, y2 - radius, argb);
        for (int i = 1; i < radius; i++) { // 모서리 대각선 점
            int j = radius - i;
            r.fill(x1 + i, y1 + j, x1 + i + 1, y1 + j + 1, argb);
            r.fill(x2 - i - 1, y1 + j, x2 - i, y1 + j + 1, argb);
            r.fill(x1 + i, y2 - j - 1, x1 + i + 1, y2 - j, argb);
            r.fill(x2 - i - 1, y2 - j - 1, x2 - i, y2 - j, argb);
        }
    }

    /** 왼쪽 여백 */
    public static int padLeft() {
        return modern() ? 6 : minimal() ? 1 : Module.PAD;
    }

    public static int padRight() {
        return modern() ? 6 : minimal() ? 1 : Module.PAD;
    }

    public static int padY() {
        return modern() ? 4 : minimal() ? 1 : Module.PAD;
    }

    /** HUD 요소 배경 */
    public static void panel(HudRenderer r, int x, int y, int w, int h) {
        if (minimal() || !OptiCore.config.background) return;
        if (modern()) {
            round(r, x, y, x + w, y + h, HUD_BG, 3);
        } else {
            r.fill(x, y, x + w, y + h, CLASSIC_BG);
        }
    }

    /** 키 입력 등 버튼 모양 상자. pressed 는 0~1 (애니메이션 진행도) */
    public static void key(HudRenderer r, int x, int y, int w, int h, float pressed) {
        if (modern()) {
            round(r, x, y, x + w, y + h, lerp(HUD_BG, withAlpha(accent(x, y), 0xE6), pressed), 3);
        } else if (minimal()) {
            if (pressed > 0.01f) round(r, x, y, x + w, y + h, withAlpha(accent(x, y), (int) (0x90 * pressed)), 2);
        } else {
            r.fill(x, y, x + w, y + h, lerp(CLASSIC_BG, 0xB0FFFFFF, pressed));
        }
    }

    /** 버튼 위 글자색 */
    public static int keyText(float pressed) {
        if (minimal()) return WHITE;
        if (modern()) return WHITE;
        return pressed > 0.5f ? 0xFF101016 : WHITE;
    }
}
