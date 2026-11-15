package dev.opticlient.fabric;

import com.mojang.blaze3d.platform.InputConstants;

/**
 * 마우스 버튼 번호를 OptiClient 기준(0 = 좌클릭, 1 = 우클릭)으로 바꾼다.
 * 1.21.11 은 GLFW 번호(좌 0, 우 1), 26.x 는 SDL 번호(좌 1, 가운데 2, 우 3)를 쓰므로
 * 게임이 정의한 상수로 판별한다.
 */
public final class MojInput {
    private MojInput() {
    }

    /** 0 = 좌클릭, 1 = 우클릭, 그 밖의 버튼은 -1 */
    public static int button(int raw) {
        if (raw == InputConstants.MOUSE_BUTTON_LEFT) return 0;
        if (raw == InputConstants.MOUSE_BUTTON_RIGHT) return 1;
        return -1;
    }
}
