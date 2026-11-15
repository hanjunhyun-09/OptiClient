package dev.opticlient.core;

import java.util.ArrayDeque;
import java.util.Deque;

/** 최근 1초 동안의 클릭 수를 센다. */
public final class CpsTracker {
    private final Deque<Long> left = new ArrayDeque<Long>();
    private final Deque<Long> right = new ArrayDeque<Long>();

    /** button 0 = 좌클릭, 1 = 우클릭 */
    public synchronized void click(int button) {
        long now = System.currentTimeMillis();
        if (button == 0) left.addLast(now);
        else if (button == 1) right.addLast(now);
    }

    public synchronized int left() {
        return prune(left);
    }

    public synchronized int right() {
        return prune(right);
    }

    private static int prune(Deque<Long> q) {
        long cutoff = System.currentTimeMillis() - 1000L;
        while (!q.isEmpty() && q.peekFirst() < cutoff) q.pollFirst();
        return q.size();
    }
}
