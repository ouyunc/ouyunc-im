package com.ouyunc.core.listener.metrics;

import java.util.List;

/**
 * Disruptor RingBuffer 只读快照（由定时监控拉取；监听器耗时为累计值，在异步消费路径上增量更新）。
 */
public record DisruptorRingMetrics(
        String eventTypeName,
        String ringName,
        int bufferSize,
        long cursor,
        long minimumGatingSequence,
        /** 近似未消费序号差（cursor - minimumGatingSequence，已下限为 0） */
        long pendingSequences,
        /** RingBuffer.remainingCapacity() */
        long remainingCapacity,
        /** 本环上 publishEvent 累计次数（异步发布路径） */
        long publishedEvents,
        boolean disruptorStarted,
        List<DisruptorListenerExecSnapshot> listenerStats
) {
}
