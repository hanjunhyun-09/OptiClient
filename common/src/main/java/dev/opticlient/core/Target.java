package dev.opticlient.core;

/** 다른 엔티티(주로 공격한 상대)의 정보. GameInfo.entity 가 채운다. */
public final class Target {
    public String name = "";
    public float health;
    public float maxHealth = 20f;
    /** 피격 애니메이션 남은 틱 (맞는 순간 10, 매 틱 1씩 줄어듦) */
    public int hurtTime;
    /** 내 눈에서 상대까지 거리 (블록) */
    public double distance;
    /** 플레이어인지 */
    public boolean player;
}
