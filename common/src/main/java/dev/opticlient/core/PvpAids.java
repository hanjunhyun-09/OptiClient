package dev.opticlient.core;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 화면에 그리는 PvP 보조 표시 (모두 "보여 주기" 만 하고 공격·움직임에는 손대지 않는다).
 * - 리치 도우미: 조준한 상대까지 거리가 공격 가능 거리(3칸) 안이면 크로스헤어 둘레가 초록, 밖이면 빨강
 * - 낮은 체력 경고: 화면 가장자리가 빨갛게 깜빡임
 * - 갑옷 경고: 내구도가 거의 다 된 갑옷 이름을 크로스헤어 위에 표시
 */
public final class PvpAids {
    /** 서바이벌 공격 가능 거리 */
    public static final double REACH = 3.0;
    /** 이 거리까지는 "가깝지만 아직 못 침" 으로 표시 */
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
    static void update(GameInfo g) {
        aim = needAim() ? g.aimDistance(LOOK) : -1;
    }

    static int aimColor(double d) {
        return d < 0 ? Theme.MUTED : d <= REACH ? 0xFF55FF55 : 0xFFFF5555;
    }

    static void render(HudRenderer r, GameInfo g, int sw, int sh) {
        HudConfig c = OptiCore.config;
        if (c.lowHpWarn) lowHp(r, g, sw, sh);
        if (c.reachAssist && g.firstPerson() && aim >= 0) reach(r, sw, sh);
        if (c.armorWarn) armor(r, g, sw, sh);
    }

    /** 크로스헤어 둘레 네 모서리 괄호 */
    private static void reach(HudRenderer r, int sw, int sh) {
        int cx = sw / 2, cy = sh / 2;
        int col = aimColor(aim);
        int d = 9, l = 3;
        for (int pass = 0; pass < 2; pass++) {
            int g = pass == 0 ? 1 : 0;
            int k = pass == 0 ? 0x90000000 : col;
            for (int sx = -1; sx <= 1; sx += 2) {
                for (int sy = -1; sy <= 1; sy += 2) {
                    int x = cx + sx * d, y = cy + sy * d;
                    // 가로 획
                    int x1 = sx < 0 ? x : x - l + 1, x2 = sx < 0 ? x + l : x + 1;
                    r.fill(x1 - g, y - g, x2 + g, y + 1 + g, k);
                    // 세로 획
                    int y1 = sy < 0 ? y : y - l + 1, y2 = sy < 0 ? y + l : y + 1;
                    r.fill(x - g, y1 - g, x + 1 + g, y2 + g, k);
                }
            }
        }
        if (OptiCore.config.reachNumber) {
            String s = String.format(Locale.ROOT, "%.2f", aim);
            r.centered(s, cx + 1, cy + d + 6, col, true);
        }
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
