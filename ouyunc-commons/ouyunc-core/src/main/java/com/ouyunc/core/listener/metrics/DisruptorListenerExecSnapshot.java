package com.ouyunc.core.listener.metrics;

/**
 * 单个 {@link com.ouyunc.core.listener.MessageEventListener} 在 Disruptor 异步路径上的执行统计快照。
 */
public record DisruptorListenerExecSnapshot(
        String listenerClassName,
        int order,
        long invocations,
        long errors,
        long totalNanos,
        long maxNanos,
        /** invocations &gt; 0 时为 totalNanos / invocations */
        double avgNanos
) {
    public double avgMs() {
        return avgNanos / 1_000_000.0;
    }

    public double maxMs() {
        return maxNanos / 1_000_000.0;
    }
}
