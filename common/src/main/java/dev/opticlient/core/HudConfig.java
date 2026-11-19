package dev.opticlient.core;

import java.util.LinkedHashMap;
import java.util.Map;

/** config/opticlient.json 에 저장되는 설정. */
public class HudConfig {
    public int textColor = 0xFFFFFFFF;
    public int accentColor = 0xFF55FFFF;
    public boolean background = true;
    /** HUD 스타일: modern / classic / minimal */
    public String style = "modern";
    /** 강조색 번호 (Theme.ACCENT_NAMES, 0 = 빨강) */
    public int accent = 0;
    /** HUD 배치 버전 (바뀌면 기본 위치로 초기화) */
    public int layoutVersion = 0;
    /** 즐겨찾기한 메뉴 카드 id */
    public java.util.List<String> favorites = new java.util.ArrayList<String>();
    /** 줌 기능 사용 */
    public boolean zoomEnabled = true;
    /** 기본 최적화: 게임을 켤 때마다 FPS 제한을 풀고 수직동기화를 끈다 */
    public boolean unlockFps = true;
    /** 타이틀 화면을 OptiClient 디자인으로 */
    public boolean customTitle = true;
    /** PvP FPS 부스트 켜짐 */
    public boolean pvpBoost = false;
    /** 1.8.9: PvP 부스트를 기본으로 켜 두었는지 (한 번만 켜고, 그 뒤로는 사용자가 정한 값을 따름) */
    public boolean boostDefaultApplied = false;
    /** PvP FPS 부스트 때 렌더 거리 */
    public int boostRenderDistance = 6;
    public boolean shadow = true;

    /** 밝기 최대 (동굴/야간 시야) */
    public boolean fullbright = false;
    /** 달리기 토글 키 사용 여부 */
    public boolean toggleSprint = true;
    /** 줌 배율 */
    public double zoomFactor = 4.0;

    // ---------------- PvP 기능 ----------------
    /** 퍼스펙티브(자유 시점): 키를 누르는 동안 몸은 그대로 두고 카메라만 돌린다 */
    public boolean freelook = true;
    /** 퍼스펙티브 키를 누를 때마다 켜고 끄기 (false 면 누르는 동안만) */
    public boolean freelookToggle = false;
    /** 웅크리기 토글 키 사용 */
    public boolean toggleSneak = true;
    /** 맞을 때 화면 흔들림 끄기 */
    public boolean noHurtCam = false;
    /** 불이 붙었을 때 화면을 가리는 불꽃을 아래로 내림 */
    public boolean lowFire = false;
    /** 인챈트 반짝임을 그리지 않음 (FPS 향상, PvP 부스트 중에는 자동) */
    public boolean noGlint = false;
    /** 1.8.9: PolyPatcher 의 HUD 캐싱을 켠다 (HUD 를 1초에 60번만 다시 그림) */
    public boolean hudCache = true;
    /** 엔티티 히트박스 표시 */
    public boolean hitboxes = false;
    /** OptiClient 크로스헤어 사용 */
    public boolean crosshair = false;
    /** 0 십자, 1 점, 2 십자+점, 3 원 */
    public int crosshairStyle = 0;
    /** 0 흰색, 1 강조색, 2 초록, 3 노랑, 4 하늘 */
    public int crosshairColor = 0;
    /** 크기 0 작게, 1 보통, 2 크게 */
    public int crosshairSize = 1;

    public Map<String, ModuleState> modules = new LinkedHashMap<String, ModuleState>();

    public static class ModuleState {
        public boolean enabled;
        public int x;
        public int y;
        /** 크기 배율 (0.5 ~ 2.0) */
        public float scale = 1f;
        /**
         * 화면 어느 쪽을 기준으로 둘지 (다른 클라이언트에서 가져온 배치). null 이면 x,y 가 왼쪽 위 기준 절대 좌표.
         * 세로 t/c/b + 가로 l/c/r (예: "br" = 오른쪽 아래). 이때 x,y 는 그 기준에 붙인 위치에서 더하는 값이라
         * 창 크기가 바뀌어도 같은 자리에 있다. 편집 화면에서 옮기면 다시 null 이 된다.
         */
        public String anchor;

        // ---------- 모듈마다 따로 정하는 설정 (메뉴 카드의 톱니) ----------
        /** 배경: 0 = 전체 설정 따름, 1 = 켬, 2 = 끔 */
        public int bg;
        /** 글자 그림자: 0 = 전체 설정 따름, 1 = 켬, 2 = 끔 */
        public int shadowMode;
        /** 글자색 번호 (Theme.TEXT_COLORS, 0 = 흰색) */
        public int color;
        /** "FPS" 같은 이름 글자 표시 */
        public boolean label = true;
        /** 모듈마다 다른 켜고 끄는 옵션 (Module.options) */
        public Map<String, Boolean> opts;

        public ModuleState() {
        }

        public ModuleState(boolean enabled, int x, int y) {
            this.enabled = enabled;
            this.x = x;
            this.y = y;
        }
    }
}
