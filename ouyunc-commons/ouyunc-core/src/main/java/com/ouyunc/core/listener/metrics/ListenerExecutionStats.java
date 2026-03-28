package com.ouyunc.core.listener.metrics;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.LongAdder;

/**
 * 异步 Disruptor 消费路径上单个监听器的累计统计（热路径仅几次原子操作）。
 */
public final class ListenerExecutionStats {

    private final String listenerClassName;
    private final int order;
    private final LongAdder invocations = new LongAdder();
    private final LongAdder errors = new LongAdder();
    private final LongAdder totalNanos = new LongAdder();
    private final AtomicLong maxNanos = new AtomicLong(Long.MIN_VALUE);

    public ListenerExecutionStats(String listenerClassName, int order) {
        this.listenerClassName = listenerClassName;
        this.order = order;
    }

    public void record(long elapsedNanos, Throwable error) {
        invocations.increment();
        if (error != null) {
            errors.increment();
        }
        totalNanos.add(elapsedNanos);
        maxNanos.accumulateAndGet(elapsedNanos, Math::max);
    }

    public DisruptorListenerExecSnapshot snapshot() {
        long n = invocations.sum();
        long tot = totalNanos.sum();
        long mx = maxNanos.get();
        if (mx == Long.MIN_VALUE) {
            mx = 0L;
        }
        double avg = n > 0 ? (double) tot / (double) n : 0.0;
        return new DisruptorListenerExecSnapshot(listenerClassName, order, n, errors.sum(), tot, mx, avg);
    }
}
