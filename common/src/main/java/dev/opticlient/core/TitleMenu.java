package dev.opticlient.core;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Random;

/**
 * OptiClient 타이틀 화면 (버전 공통).
 * 그라데이션 배경 + 떠오르는 입자 + 도트 로고 + 애니메이션 버튼.
 * 각 버전의 Screen 은 그리기와 클릭만 넘기고, 돌려받은 동작(싱글/멀티/설정/종료)을 실행한다.
 */
public final class TitleMenu {
    public static final int NONE = -1;
    public static final int SINGLE = 0;
    public static final int MULTI = 1;
    public static final int OPTIONS = 2;
    public static final int QUIT = 3;
    public static final int VANILLA = 4;

    private static final String[] LABELS = {"싱글플레이", "멀티플레이", "설정", "게임 종료"};
    private static final int BTN_W = 190;
    private static final int BTN_H = 20;

    // 5x7 도트 글자
    private static final String[][] GLYPHS = {
            {".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."}, // O
            {"####.", "#...#", "#...#", "####.", "#....", "#....", "#...."}, // P
            {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."}, // T
            {"#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"}, // I
            {".####", "#....", "#....", "#....", "#....", "#....", ".####"}, // C
            {"#....", "#....", "#....", "#....", "#....", "#....", "#####"}, // L
            {"#####", "#....", "#....", "####.", "#....", "#....", "#####"}, // E
            {"#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"}, // N
    };
    private static final int[] WORD = {0, 1, 2, 3, 4, 5, 3, 6, 7, 2}; // O P T I C L I E N T

    private static final int PARTICLES = 70;
    private final float[] px = new float[PARTICLES];
    private final float[] py = new float[PARTICLES];
    private final float[] speed = new float[PARTICLES];
    private final int[] size = new int[PARTICLES];
    private final float[] phase = new float[PARTICLES];
    private final int[] color = new int[PARTICLES];

    private final String versionText;
    private final long openedAt = System.currentTimeMillis();
    private final float[] hover = new float[LABELS.length + 1];
    private long lastNanos;
    private HudRenderer measure;
    private int sw;
    private int sh;
    private final SimpleDateFormat clock = new SimpleDateFormat("a h:mm", Locale.KOREAN);

    public TitleMenu(String versionText) {
        this.versionText = versionText;
        Random rnd = new Random(7);
        int[] palette = {0xE5383B, 0xFF6B4A, 0xFF8A3D, 0xFFFFFF};
        for (int i = 0; i < PARTICLES; i++) {
            px[i] = rnd.nextFloat();
            py[i] = rnd.nextFloat();
            speed[i] = 0.015f + rnd.nextFloat() * 0.045f;
            size[i] = 1 + rnd.nextInt(3);
            phase[i] = rnd.nextFloat() * 6.28f;
            color[i] = palette[rnd.nextInt(palette.length)];
        }
    }

    private static float ease(float t) {
        t = Math.max(0f, Math.min(1f, t));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    // ---------------- 위치 ----------------

    private int logoPx() {
        return Math.max(2, Math.min(6, sw / 100));
    }

    private int buttonsTop() {
        return sh / 2 + 4;
    }

    /** i: 0 싱글, 1 멀티, 2 설정(왼쪽 반), 3 종료(오른쪽 반) */
    private int[] buttonRect(int i) {
        int x = (sw - BTN_W) / 2;
        int y = buttonsTop();
        if (i < 2) return new int[]{x, y + i * (BTN_H + 5), x + BTN_W, y + i * (BTN_H + 5) + BTN_H};
        int half = (BTN_W - 5) / 2;
        int by = y + 2 * (BTN_H + 5);
        return i == 2 ? new int[]{x, by, x + half, by + BTN_H} : new int[]{x + BTN_W - half, by, x + BTN_W, by + BTN_H};
    }

    private int[] vanillaLink(HudRenderer r) {
        String s = "기본 타이틀 화면";
        int w = r.width(s);
        int y = buttonRect(3)[3] + 12;
        return new int[]{(sw - w) / 2, y, (sw + w) / 2, y + 9};
    }

    // ---------------- 그리기 ----------------

    public void render(HudRenderer r, int mx, int my, int screenW, int screenH, int fps) {
        sw = screenW;
        sh = screenH;
        measure = r;
        long now = System.nanoTime();
        float dt = lastNanos == 0 ? 0.016f : Math.min(0.1f, (now - lastNanos) / 1e9f);
        lastNanos = now;
        float t = (System.currentTimeMillis() - openedAt) / 1000f;
        // 큰 수(현재 시각)를 float 로 바꾸면 소수점이 사라지므로 작은 범위로 줄여서 쓴다
        float time = (System.currentTimeMillis() % 1000000L) / 1000f;

        // 배경 그라데이션 (위: 짙은 남색 → 아래: 짙은 빨강)
        int bands = 48;
        for (int i = 0; i < bands; i++) {
            int y1 = sh * i / bands, y2 = sh * (i + 1) / bands;
            r.fill(0, y1, sw, y2, Theme.lerp(0xFF0A0B10, 0xFF240A0E, i / (float) (bands - 1)));
        }

        // 로고 뒤 숨쉬는 빛
        int ps = logoPx();
        int logoW = (WORD.length * 6 - 1) * ps;
        int logoH = 7 * ps;
        // 엠블럼(도트 로고) + 글자 로고를 한 줄로 가운데 정렬
        int eps = Math.max(1, Math.round(ps * 0.55f));
        int embW = Logo.SIZE * eps;
        int embGap = ps * 3;
        int logoX = (sw - logoW + embW + embGap) / 2;
        int logoY = (int) (buttonsTop() - logoH - 34 - 18 * (1f - ease(t / 0.7f)));
        int cx = sw / 2, cy = logoY + logoH / 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(time * 1.6f);
        for (int k = 6; k >= 1; k--) {
            int gw = (int) (logoW / 2 + k * 14 + pulse * 8);
            int gh = (int) (logoH / 2 + k * 9 + pulse * 5);
            Theme.round(r, cx - gw, cy - gh, cx + gw, cy + gh, Theme.withAlpha(0xE5383B, 5 + (int) (pulse * 4)), Math.min(gh, 24));
        }

        // 떠오르는 입자
        for (int i = 0; i < PARTICLES; i++) {
            float y = (py[i] - time * speed[i]) % 1f;
            if (y < 0) y += 1f;
            float x = px[i] + 0.015f * (float) Math.sin(time * 0.8f + phase[i]);
            int a = (int) (60 + 120 * (0.5f + 0.5f * Math.sin(time * 2.2f + phase[i])));
            int sx = (int) (x * sw), sy = (int) (y * sh);
            r.fill(sx, sy, sx + size[i], sy + size[i], Theme.withAlpha(color[i], a));
        }

        // 아래쪽 어두운 그라데이션 + 위쪽 가는 빨간 선
        for (int i = 0; i < 16; i++) r.fill(0, sh - 40 + i * 40 / 16, sw, sh - 40 + (i + 1) * 40 / 16, Theme.withAlpha(0x000000, i * 6));
        r.fill(0, 0, (int) (sw * ease(t / 0.9f)), 1, Theme.accent());

        // 로고: OPTI 흰색 + CLIENT 빨간색, 아래로 그림자
        int logoAlpha = (int) (255 * ease(t / 0.6f));
        if (logoAlpha > 10) {
            for (int pass = 0; pass < 2; pass++) {
                int off = pass == 0 ? Math.max(1, ps / 2) : 0;
                for (int g = 0; g < WORD.length; g++) {
                    int col;
                    if (pass == 0) col = Theme.withAlpha(0x000000, logoAlpha * 3 / 5);
                    else col = Theme.withAlpha(g < 4 ? 0xFFFFFF : Theme.accent(logoX + g * 6 * ps, logoY) & 0xFFFFFF, logoAlpha);
                    drawGlyph(r, GLYPHS[WORD[g]], logoX + g * 6 * ps + off, logoY + off, ps, col);
                }
            }
            Logo.draw(r, logoX - embGap - embW, cy - embW / 2, eps, logoAlpha);
            String sub = "MINECRAFT  최적화 클라이언트";
            int subA = (int) (220 * ease((t - 0.25f) / 0.6f));
            if (subA > 10) r.centered(sub, sw / 2, logoY + logoH + 9, Theme.withAlpha(0xB8BCC8, subA), false);
            int barW = (int) (60 * ease((t - 0.35f) / 0.6f));
            r.fill(sw / 2 - barW / 2, logoY + logoH + 21, sw / 2 + barW / 2, logoY + logoH + 22, Theme.accent());
        }

        // 버튼 (하나씩 올라오며 나타남)
        for (int i = 0; i < LABELS.length; i++) {
            float appear = ease((t - 0.35f - i * 0.08f) / 0.45f);
            if (appear <= 0.02f) continue;
            int[] b = buttonRect(i);
            int lift = (int) ((1f - appear) * 12);
            boolean over = inside(mx, my, b);
            hover[i] += ((over ? 1f : 0f) - hover[i]) * Math.min(1f, dt * 14f);
            int y1 = b[1] + lift, y2 = b[3] + lift;
            int alpha = (int) (appear * 255);
            int bg = Theme.lerp(0xD0181A20, 0xE6262830, hover[i]);
            Theme.round(r, b[0], y1, b[2], y2, Theme.withAlpha(bg, (bg >>> 24) * alpha / 255), 3);
            int bar = (int) (2 + hover[i] * (i >= 2 ? 3 : 5));
            Theme.round(r, b[0], y1, b[0] + bar, y2, Theme.withAlpha(Theme.accent(b[0], y1), alpha), 1);
            int textX = b[0] + 12 + (int) (hover[i] * 5);
            int tc = i == QUIT ? Theme.lerp(0xFFC9A0A3, 0xFFFF7A7A, hover[i]) : Theme.lerp(0xFFDADCE3, 0xFFFFFFFF, hover[i]);
            if (alpha > 10) {
                if (i >= 2) r.centered(LABELS[i], (b[0] + b[2]) / 2 + (int) (hover[i] * 2), y1 + 6, Theme.withAlpha(tc, alpha), false);
                else {
                    r.text(LABELS[i], textX, y1 + 6, Theme.withAlpha(tc, alpha), false);
                    if (hover[i] > 0.05f) r.text("›", b[2] - 12 - (int) ((1 - hover[i]) * 6), y1 + 6, Theme.withAlpha(Theme.accent(), (int) (hover[i] * 255)), false);
                }
            }
        }

        // 기본 타이틀 링크
        float linkA = ease((t - 0.8f) / 0.5f);
        int[] link = vanillaLink(r);
        boolean overLink = inside(mx, my, link);
        if (linkA > 0.05f) {
            r.text("기본 타이틀 화면", link[0], link[1], Theme.withAlpha(overLink ? 0xFFFFFF : 0x8E929E, (int) (linkA * 255)), false);
            if (overLink) r.fill(link[0], link[3], link[2], link[3] + 1, Theme.accent());
        }

        // 아래 정보
        String left = "OptiClient 1.0  ·  " + versionText;
        r.text(left, 6, sh - 12, 0xFF8E929E, false);
        String right = clock.format(new Date()) + "  ·  " + fps + " FPS";
        r.text(right, sw - 6 - r.width(right), sh - 12, 0xFF8E929E, false);

        // 열릴 때 검은 화면에서 밝아짐
        int fade = (int) (255 * (1f - ease(t / 0.5f)));
        if (fade > 0) r.fill(0, 0, sw, sh, Theme.withAlpha(0x000000, fade));
        r.flush();
    }

    private static void drawGlyph(HudRenderer r, String[] g, int x, int y, int px, int color) {
        for (int row = 0; row < g.length; row++) {
            String line = g[row];
            int col = 0;
            while (col < line.length()) {
                if (line.charAt(col) != '#') {
                    col++;
                    continue;
                }
                int start = col;
                while (col < line.length() && line.charAt(col) == '#') col++;
                r.fill(x + start * px, y + row * px, x + col * px, y + (row + 1) * px, color);
            }
        }
    }

    // ---------------- 입력 ----------------

    /** 클릭한 동작 (NONE 이면 아무것도 아님) */
    public int click(int mx, int my) {
        for (int i = 0; i < LABELS.length; i++) if (inside(mx, my, buttonRect(i))) return i;
        if (measure != null && inside(mx, my, vanillaLink(measure))) return VANILLA;
        return NONE;
    }

    private static boolean inside(int mx, int my, int[] r) {
        return mx >= r[0] && my >= r[1] && mx < r[2] && my < r[3];
    }
}
