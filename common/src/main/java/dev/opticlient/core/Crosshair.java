package dev.opticlient.core;

/**
 * OptiClient 크로스헤어 (켜면 기본 크로스헤어 대신 그린다).
 * 모양: 십자 / 점 / 십자+점 / 원. 색과 크기는 설정 탭에서 바꾼다.
 * 1.9+ 는 공격 쿨다운이 차는 동안 아래에 작은 막대를 보여 준다.
 */
public final class Crosshair {
    public static final String[] STYLES = {"십자", "점", "십자+점", "원"};
    public static final String[] COLORS = {"흰색", "강조색", "초록", "노랑", "하늘"};
    public static final String[] SIZES = {"작게", "보통", "크게"};

    private static final int OUTLINE = 0x90000000;

    private Crosshair() {
    }

    /** 리치 도우미: 다른 플레이어가 3칸 안에 조준되면 */
    public static final int IN_REACH = 0xFF55FF55;

    static int color() {
        if (OptiCore.config.reachAssist && PvpAids.inReach()) return IN_REACH;
        switch (OptiCore.config.crosshairColor) {
            case 1: return Theme.accent();
            case 2: return 0xFF55FF55;
            case 3: return 0xFFFFFF55;
            case 4: return 0xFF55FFFF;
            default: return 0xFFFFFFFF;
        }
    }

    static void render(HudRenderer r, GameInfo g, int sw, int sh) {
        HudConfig c = OptiCore.config;
        int size = Math.max(0, Math.min(2, c.crosshairSize));
        int len = new int[]{3, 5, 7}[size];
        int gap = new int[]{1, 2, 2}[size];
        int cx = sw / 2;
        int cy = sh / 2;
        int col = color();
        int style = Math.max(0, Math.min(3, c.crosshairStyle));

        // 테두리 먼저, 그 위에 색
        for (int pass = 0; pass < 2; pass++) {
            int o = pass == 0 ? 1 : 0;
            int k = pass == 0 ? OUTLINE : col;
            if (style == 0 || style == 2) {
                rect(r, cx - gap - len, cy, cx - gap, cy + 1, o, k);
                rect(r, cx + 1 + gap, cy, cx + 1 + gap + len, cy + 1, o, k);
                rect(r, cx, cy - gap - len, cx + 1, cy - gap, o, k);
                rect(r, cx, cy + 1 + gap, cx + 1, cy + 1 + gap + len, o, k);
            }
            if (style == 1) rect(r, cx - 1, cy - 1, cx + 2, cy + 2, o, k);
            if (style == 2 || style == 3) rect(r, cx, cy, cx + 1, cy + 1, o, k);
            if (style == 3) ring(r, cx, cy, len - 1, o, k);
        }

        // 공격 쿨다운 막대 (1.9+)
        float cd = g.attackCooldown();
        if (cd < 0.99f) {
            int w = 16;
            int y = cy + gap + len + 4;
            r.fill(cx - w / 2 - 1, y - 1, cx + w / 2 + 1, y + 2, OUTLINE);
            r.fill(cx - w / 2, y, cx - w / 2 + Math.round(w * Math.max(0f, cd)), y + 1, col);
        }
    }

    private static void rect(HudRenderer r, int x1, int y1, int x2, int y2, int grow, int argb) {
        r.fill(x1 - grow, y1 - grow, x2 + grow, y2 + grow, argb);
    }

    private static void ring(HudRenderer r, int cx, int cy, int radius, int grow, int argb) {
        for (int dy = -radius - 1; dy <= radius + 1; dy++) {
            for (int dx = -radius - 1; dx <= radius + 1; dx++) {
                double d = Math.sqrt(dx * dx + dy * dy);
                if (Math.abs(d - radius) < 0.5) rect(r, cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, grow, argb);
            }
        }
    }
}
