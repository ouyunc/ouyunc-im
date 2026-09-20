package com.ouyunc.message.monitor;

import java.util.concurrent.atomic.AtomicLong;

/**
 * SERVER QoS 取消路径计数：本机取消、跨节点取消、定位/转发失败、非法内部控制包。
 */
public final class QosRetryCancelMetrics {

    private static final AtomicLong LOCAL_CANCEL = new AtomicLong();
    private static final AtomicLong CLUSTER_CANCEL_HIT = new AtomicLong();
    private static final AtomicLong CLUSTER_CANCEL_MISS = new AtomicLong();
    private static final AtomicLong ORIGIN_LOCATE_FAIL = new AtomicLong();
    private static final AtomicLong FORWARD_FAIL = new AtomicLong();
    private static final AtomicLong FORWARD_RETRY = new AtomicLong();
    private static final AtomicLong INVALID_PACKET = new AtomicLong();
    private static final AtomicLong ACK_DISPATCH_REJECT = new AtomicLong();

    private QosRetryCancelMetrics() {
    }

    public static void localCancel() {
        LOCAL_CANCEL.incrementAndGet();
    }

    public static void clusterCancelHit() {
        CLUSTER_CANCEL_HIT.incrementAndGet();
    }

    public static void clusterCancelMiss() {
        CLUSTER_CANCEL_MISS.incrementAndGet();
    }

    public static void originLocateFail() {
        ORIGIN_LOCATE_FAIL.incrementAndGet();
    }

    public static void forwardFail() {
        FORWARD_FAIL.incrementAndGet();
    }

    public static void forwardRetry() {
        FORWARD_RETRY.incrementAndGet();
    }

    public static void invalidPacket() {
        INVALID_PACKET.incrementAndGet();
    }

    public static void ackDispatchReject() {
        ACK_DISPATCH_REJECT.incrementAndGet();
    }

    public static Snapshot snapshot() {
        return new Snapshot(
                LOCAL_CANCEL.get(),
                CLUSTER_CANCEL_HIT.get(),
                CLUSTER_CANCEL_MISS.get(),
                ORIGIN_LOCATE_FAIL.get(),
                FORWARD_FAIL.get(),
                FORWARD_RETRY.get(),
                INVALID_PACKET.get(),
                ACK_DISPATCH_REJECT.get());
    }

    public record Snapshot(
            long localCancel,
            long clusterCancelHit,
            long clusterCancelMiss,
            long originLocateFail,
            long forwardFail,
            long forwardRetry,
            long invalidPacket,
            long ackDispatchReject
    ) {
    }
}
