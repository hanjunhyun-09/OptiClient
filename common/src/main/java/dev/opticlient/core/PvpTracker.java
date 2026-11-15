package dev.opticlient.core;

/**
 * 리치·콤보·타깃 계산 (버전 공통).
 * - 공격하는 순간 버전 코드가 onAttack 으로 상대 id 와 거리를 넘겨준다.
 * - 매 틱 tick 에서 상대의 피격 애니메이션이 새로 시작됐는지 보고 "맞힘"을 판정한다.
 *   (빗나간 공격·무적 시간 중 공격은 세지 않는다)
 * - 내가 맞으면 콤보가 0 이 되고, 3초 동안 아무도 안 맞히면 끝난다.
 */
public final class PvpTracker {
    private static final long HIT_WINDOW_MS = 700;
    private static final long COMBO_TIMEOUT_MS = 3000;
    private static final long TARGET_SHOW_MS = 6000;

    private final Target scratch = new Target();

    private int targetId = -1;
    private long attackAt;
    private double pendingReach = -1;
    /** 아직 맞힘 판정을 못 받은 공격이 있는지 */
    private boolean pendingAttack;
    private int prevTargetHurt;
    private int prevSelfHurt;

    private double reach = -1;
    private long reachAt;
    private int combo;
    private long comboAt;
    private long targetSeenAt;

    /** 마지막 타깃 정보 (TargetHud 가 그린다) */
    public final Target target = new Target();
    private boolean hasTarget;

    /** 공격하는 순간 호출 */
    public void onAttack(int entityId, double distance) {
        if (entityId != targetId) {
            targetId = entityId;
            prevTargetHurt = 0;
        }
        attackAt = System.currentTimeMillis();
        pendingReach = distance;
        pendingAttack = true;
    }

    private void landHit(long now) {
        combo = now - comboAt <= COMBO_TIMEOUT_MS ? combo + 1 : 1;
        comboAt = now;
        if (pendingReach >= 0) {
            reach = pendingReach;
            reachAt = now;
        }
        pendingAttack = false;
    }

    /** 매 틱 호출 (플레이어가 있을 때) */
    public void tick(GameInfo g) {
        long now = System.currentTimeMillis();
        int self = g.hurtTime();
        if (self > prevSelfHurt) combo = 0; // 맞으면 콤보 끊김
        prevSelfHurt = self;

        if (targetId >= 0 && g.entity(targetId, scratch)) {
            if (scratch.hurtTime > prevTargetHurt && pendingAttack && now - attackAt <= HIT_WINDOW_MS) {
                landHit(now); // 내 공격 직후 상대가 새로 맞음
            }
            prevTargetHurt = scratch.hurtTime;
            copy(scratch, target);
            hasTarget = true;
            targetSeenAt = attackAt;
        } else if (targetId >= 0) {
            // 상대가 죽었거나 멀어져서 사라짐: 공격 직후라면 마지막 한 방으로 센다
            if (pendingAttack && now - attackAt <= HIT_WINDOW_MS && target.health > 0) landHit(now);
            target.health = 0;
            prevTargetHurt = 0;
        }
        if (now - comboAt > COMBO_TIMEOUT_MS) combo = 0;
    }

    public int combo() {
        return combo;
    }

    /** 마지막으로 맞힌 거리. 3초가 지나거나 없으면 -1 */
    public double reach() {
        return reach >= 0 && System.currentTimeMillis() - reachAt <= COMBO_TIMEOUT_MS ? reach : -1;
    }

    /** 최근(6초)에 공격한 상대가 있으면 true */
    public boolean showTarget() {
        return hasTarget && System.currentTimeMillis() - targetSeenAt <= TARGET_SHOW_MS;
    }

    /** 게임에서 나가면 초기화 */
    public void reset() {
        targetId = -1;
        combo = 0;
        reach = -1;
        hasTarget = false;
        prevSelfHurt = 0;
        prevTargetHurt = 0;
        pendingAttack = false;
    }

    private static void copy(Target a, Target b) {
        b.name = a.name;
        b.health = a.health;
        b.maxHealth = a.maxHealth;
        b.hurtTime = a.hurtTime;
        b.distance = a.distance;
        b.player = a.player;
    }

    /** 눈 위치에서 상대 충돌 상자의 가장 가까운 점까지 거리 (리치) */
    public static double reach(double ex, double ey, double ez,
                               double minX, double minY, double minZ, double maxX, double maxY, double maxZ) {
        double dx = ex < minX ? minX - ex : ex > maxX ? ex - maxX : 0;
        double dy = ey < minY ? minY - ey : ey > maxY ? ey - maxY : 0;
        double dz = ez < minZ ? minZ - ez : ez > maxZ ? ez - maxZ : 0;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
