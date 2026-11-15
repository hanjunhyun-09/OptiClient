package dev.opticlient.core;

/**
 * PvP FPS 부스트: 버전마다 그래픽 설정을 FPS 위주로 바꾸고, 끄면 원래 값으로 돌린다.
 * 바꾸는 항목: 렌더/시뮬레이션 거리, 파티클 최소, 구름·그림자 끔, 빠른 그래픽,
 * 부드러운 조명 끔, 바이옴 혼합 0, 수직동기화 끔, FPS 제한 해제, 엔티티 거리 축소.
 */
public interface PerfBoost {
    void apply(boolean on);

    /** 기본 최적화 (항상): FPS 제한 해제 + 수직동기화 끔 (+ 가만히 있을 때 FPS 낮추기 끔) */
    void baseline();
}
