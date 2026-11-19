package dev.opticlient.core;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** 버전마다 다른 게임 상태 조회를 감싸는 인터페이스. */
public interface GameInfo {
    enum Key { FORWARD, LEFT, BACK, RIGHT, JUMP, SNEAK, ATTACK, USE }

    int fps();

    double x();

    double y();

    double z();

    float yaw();

    String biome();

    /** 서버 핑(ms). 알 수 없으면 -1 */
    int ping();

    /** 수평 이동 속도 (블록/초) */
    double speed();

    boolean key(Key k);

    /** slot 0=투구, 1=흉갑, 2=레깅스, 3=부츠. 비어 있으면 null */
    Object armor(int slot);

    /** 주 손 아이템. 비어 있으면 null */
    Object mainHand();

    int damage(Object stack);

    int maxDamage(Object stack);

    int count(Object stack);

    /** "신속 II: 1:23" 형식의 효과 목록 */
    List<String> effects();

    // ---------------- PvP 정보 (버전마다 구현) ----------------

    /** 내 체력 */
    default float health() {
        return 20f;
    }

    default float maxHealth() {
        return 20f;
    }

    /** 내 피격 애니메이션 남은 틱 (맞는 순간 10) */
    default int hurtTime() {
        return 0;
    }

    /** id 엔티티 정보를 out 에 채운다. 없거나 살아 있는 엔티티가 아니면 false */
    default boolean entity(int id, Target out) {
        return false;
    }

    /** 인벤토리(핫바 포함) 아이템 개수: 아이템 id 경로(golden_apple 등) → 개수 */
    default Map<String, Integer> inventory() {
        return Collections.emptyMap();
    }

    /** 인벤토리 칸 아이템 (0~8 핫바, 9~35 가방). 비어 있으면 null */
    default Object slot(int i) {
        return null;
    }

    /**
     * 몸이 보는 방향으로 max 칸까지 쏜 선이 처음 맞는 살아 있는 엔티티(충돌 상자)까지의 거리.
     * 블록에 가리거나 없으면 -1. (공격 판정과 같은 방식: 눈 위치 → 상자 표면). playersOnly 면 다른 플레이어만
     */
    default double aimDistance(double max, boolean playersOnly) {
        return -1;
    }

    /** 배고픔 (0~20) */
    default int food() {
        return 20;
    }

    /** 포화도 */
    default float saturation() {
        return 0f;
    }

    /** 접속한 서버 주소. 싱글플레이면 null */
    default String server() {
        return null;
    }

    /** 1인칭 시점인지 (크로스헤어 표시용) */
    default boolean firstPerson() {
        return true;
    }

    /** 공격 쿨다운 (1 = 준비됨). 1.8.9 처럼 쿨다운이 없으면 1 */
    default float attackCooldown() {
        return 1f;
    }
}
