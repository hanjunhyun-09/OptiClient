package dev.opticlient.core;

/**
 * OptiClient 로고 (16x16 도트 엠블럼: 빨간 배지 + 흰 고리 + 번개).
 * 런처 아이콘(opticlient.ico)과 같은 그림이다.
 * B = 배경(위→아래 빨강 그라데이션), O = 흰 고리, Z = 번개, D = 번개 테두리.
 */
public final class Logo {
    private Logo() {
    }

    public static final int SIZE = 16;

    private static final String[] ROWS = {
            "................",
            "...BBBBBBDDBB...",
            "..BBBBOODZZDBB..",
            ".BBBOOODZZDOBBB.",
            ".BBOOODZZDOOOBB.",
            ".BBOODZZDDDOOBB.",
            ".BOODZZZZZZDOOB.",
            ".BOODZZZZZZDOOB.",
            ".BOOBDDDZZDBOOB.",
            ".BOOBBDZZDBBOOB.",
            ".BBOODZZDBBOOBB.",
            ".BBODZZDBBOOOBB.",
            ".BBBDZDOOOOOBBB.",
            "..BBBDOOOOBBBB..",
            "...BBBBBBBBBB...",
            "................"};

    private static int color(char c, int row) {
        switch (c) {
            case 'B':
                return Theme.lerp(0xFFF2484E, 0xFF9E1018, row / 15f);
            case 'D':
                return 0xFF5E070C;
            case 'O':
                return 0xFFFFFFFF;
            case 'Z':
                return 0xFFFFD24A;
            default:
                return 0;
        }
    }

    /** (x, y) 에 한 칸 px 픽셀 크기로 그린다. alpha 0~255 */
    public static void draw(HudRenderer r, int x, int y, int px, int alpha) {
        for (int row = 0; row < ROWS.length; row++) {
            String line = ROWS[row];
            int col = 0;
            while (col < line.length()) {
                char c = line.charAt(col);
                if (c == '.') {
                    col++;
                    continue;
                }
                int start = col; // 같은 글자 연속은 한 번에 칠한다
                while (col < line.length() && line.charAt(col) == c) col++;
                int argb = color(c, row);
                if (alpha < 255) argb = (argb & 0xFFFFFF) | (((argb >>> 24) * alpha / 255) << 24);
                r.fill(x + start * px, y + row * px, x + col * px, y + (row + 1) * px, argb);
            }
        }
    }
}
