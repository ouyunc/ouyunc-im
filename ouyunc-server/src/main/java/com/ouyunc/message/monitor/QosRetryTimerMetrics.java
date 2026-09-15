package com.ouyunc.message.monitor;

/**
 * QoS SERVER 模式定时重试任务（{@code timerTaskCaffeine}）监控快照。
 */
public record QosRetryTimerMetrics(
        /** 当前在缓存中的重试任务数（≈ 等待 ACK 的 QoS 重推数） */
        long activeTasks,
        /** 缓存容量上限 */
        int maxCapacity,
        /** 当前占用比例 activeTasks / maxCapacity，maxCapacity=0 时为 0 */
        double utilization,
        /** 因容量满（RemovalCause.SIZE）被淘汰的累计次数 */
        long sizeEvictionCount,
        long hitCount,
        long missCount,
        long evictionCount,
        long requestCount,
        /** 时间轮触发滞后样本数 */
        long triggerDelayCount,
        /** 时间轮触发滞后平均毫秒 */
        double triggerDelayAvgMs,
        /** 时间轮触发滞后最大毫秒 */
        double triggerDelayMaxMs,
        /** 向有界执行器提交被拒绝累计 */
        long executorRejectCount
) {

    public static final String CACHE_NAME = "timerTaskCaffeine";

    public long remainingCapacity() {
        if (maxCapacity <= 0) {
            return Long.MAX_VALUE;
        }
        return Math.max(0, maxCapacity - activeTasks);
    }
}
