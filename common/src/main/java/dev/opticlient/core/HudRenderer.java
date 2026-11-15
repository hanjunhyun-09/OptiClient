package dev.opticlient.core;

/**
 * 버전마다 다른 그리기 API를 감싸는 최소 인터페이스.
 * 1.8.9 / 1.20.1 / 1.21.1 모드가 각각 구현한다.
 */
public interface HudRenderer {
    void fill(int x1, int y1, int x2, int y2, int argb);

    void text(String s, int x, int y, int argb, boolean shadow);

    int width(String s);

    int lineHeight();

    /** stack은 각 버전의 ItemStack 객체 (null이면 그리지 않음) */
    void item(Object stack, int x, int y);

    /** 확대/축소한 글자 (기본 구현은 원래 크기) */
    default void scaledText(String s, int x, int y, int argb, boolean shadow, float scale) {
        text(s, x, y, argb, shadow);
    }

    /** 이 사각형 밖은 그리지 않는다 (스크롤 영역) */
    default void scissor(int x1, int y1, int x2, int y2) {
    }

    default void endScissor() {
    }

    /** (x, y) 로 옮기고 scale 배로 키운다. 그 뒤 (0, 0) 기준으로 그린다. popTransform 과 짝 */
    void pushTransform(int x, int y, float scale);

    void popTransform();

    /** 모아 둔 그리기를 지금 GPU 로 보낸다 (묶어 그리기를 하는 버전만 의미 있음) */
    default void flush() {
    }

    /** 지금까지 그린 것 위에 다음 것을 그리도록 레이어를 올린다 (1.20+ 는 글자를 나중에 몰아 그리기 때문) */
    default void nextLayer() {
    }

    default void outline(int x1, int y1, int x2, int y2, int argb) {
        fill(x1, y1, x2, y1 + 1, argb);
        fill(x1, y2 - 1, x2, y2, argb);
        fill(x1, y1, x1 + 1, y2, argb);
        fill(x2 - 1, y1, x2, y2, argb);
    }

    default void centered(String s, int cx, int y, int argb, boolean shadow) {
        text(s, cx - width(s) / 2, y, argb, shadow);
    }
}
