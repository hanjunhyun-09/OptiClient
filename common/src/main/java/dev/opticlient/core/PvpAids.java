package dev.opticlient.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 화면에 그리는 PvP 보조 표시 (모두 "보여 주기" 만 하고 공격·움직임에는 손대지 않는다).
 * - 리치 도우미: 조준한 다른 플레이어가 공격 가능 거리(3칸) 안이면 크로스헤어가 초록 (Crosshair.color)
 * - 낮은 체력 경고: 화면 가장자리가 빨갛게 깜빡임
 * - 갑옷 경고: 내구도가 거의 다 된 갑옷 이름을 크로스헤어 위에 표시
 */
public final class PvpAids {
    /** 서바이벌 공격 가능 거리 */
    public static final double REACH = 3.0;
    /** 조준 거리 HUD 가 보여 주는 최대 거리 */
    private static final double LOOK = 6.0;

    /** 이번 프레임 조준 거리 (-1 = 없음). 조준 거리 HUD 도 이 값을 쓴다 */
    static double aim = -1;

    private static final String[] ARMOR_NAMES = {"투구", "흉갑", "레깅스", "부츠"};

    private PvpAids() {
    }

    static boolean needAim() {
        HudConfig.ModuleState s = OptiCore.config.modules.get("aim");
        return OptiCore.config.reachAssist || s != null && s.enabled;
    }

    /** HUD 를 그리기 전에 한 번 */
    /** 자동 테스트용: 싱글플레이에는 다른 플레이어가 없어서 몹으로 확인 */
    public static boolean anyEntityForTest;

    static void update(GameInfo g) {
        aim = needAim() ? g.aimDistance(LOOK, !anyEntityForTest) : -1; // 다른 플레이어만
    }

    /** 조준한 플레이어가 칠 수 있는 거리 안인지 */
    public static boolean inReach() {
        return aim >= 0 && aim <= REACH;
    }

    static void render(HudRenderer r, GameInfo g, int sw, int sh) {
        HudConfig c = OptiCore.config;
        if (c.lowHpWarn) lowHp(r, g, sw, sh);
        if (c.armorWarn) armor(r, g, sw, sh);
    }

    /** 화면 가장자리 빨간 테두리 (체력이 낮을수록 진하게, 깜빡임) */
    private static void lowHp(HudRenderer r, GameInfo g, int sw, int sh) {
        float hp = g.health();
        if (hp <= 0 || hp > OptiCore.config.lowHpAt) return;
        double t = System.currentTimeMillis() / 1000.0;
        float pulse = (float) (0.65 + 0.35 * Math.sin(t * 6));
        float strength = 1f - Math.max(0f, hp - 1f) / Math.max(1f, OptiCore.config.lowHpAt); // 낮을수록 1 에 가까움
        int max = Math.round((0x70 + 0x60 * strength) * pulse);
        int steps = 18;
        for (int i = 0; i < steps; i++) {
            int a = max * (steps - i) / steps;
            int col = (a << 24) | 0xE01818;
            r.fill(0, i, sw, i + 1, col);
            r.fill(0, sh - i - 1, sw, sh - i, col);
            r.fill(i, steps, i + 1, sh - steps, col);
            r.fill(sw - i - 1, steps, sw - i, sh - steps, col);
        }
    }

    /** 내구도가 거의 다 된 갑옷 */
    private static void armor(HudRenderer r, GameInfo g, int sw, int sh) {
        List<String> low = new ArrayList<String>();
        for (int i = 0; i < 4; i++) {
            Object it = g.armor(i);
            if (it == null) continue;
            int max = g.maxDamage(it);
            if (max <= 0) continue;
            int pct = Math.round((max - g.damage(it)) * 100f / max);
            if (pct <= OptiCore.config.armorWarnPct) low.add(ARMOR_NAMES[i] + " " + pct + "%");
        }
        if (low.isEmpty()) return;
        boolean on = (System.currentTimeMillis() / 400) % 2 == 0;
        String s = "갑옷 내구도 부족: " + String.join(" · ", low);
        r.centered(s, sw / 2, sh / 2 - 34, on ? 0xFFFF5555 : 0xFFFFD166, true);
    }
}
