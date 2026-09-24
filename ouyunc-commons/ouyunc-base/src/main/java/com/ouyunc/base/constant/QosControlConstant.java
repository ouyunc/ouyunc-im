package com.ouyunc.base.constant;

/** QoS 控制通道的容量和超时；与下行重发执行器隔离。 */
public final class QosControlConstant {
    public static final int MAX_IN_FLIGHT = 1024;
    public static final int MAX_PER_CHANNEL = 16;
    public static final int TIMEOUT_SECONDS = 15;
    /** ACK 过载仅做有界短暂缓冲，不能无限占用 Packet 和连接引用。 */
    public static final int MAX_PENDING_ACKS = 2048;
    public static final int MAX_PENDING_ACKS_PER_CHANNEL = 32;
    public static final int ACK_RETRY_ATTEMPTS = 20;
    public static final long ACK_RETRY_DELAY_MS = 100L;
    public static final int CANCEL_FORWARD_MAX_ATTEMPTS = 2;
    public static final int CANCEL_FORWARD_DELAY_SECONDS = 1;

    private QosControlConstant() {
    }
}
