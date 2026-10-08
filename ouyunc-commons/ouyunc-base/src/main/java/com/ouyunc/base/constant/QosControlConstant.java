package com.ouyunc.base.constant;

/** QoS 控制通道的容量和超时；与下行重发执行器隔离。 */
public final class QosControlConstant {

    /** 下行重试默认次数；负数配置也回落到该值，避免无限定时任务持续读取消息。 */
    public static final int DEFAULT_DOWNLINK_RETRY_ATTEMPTS = 3;

    /**
     * 全节点允许同时处理的客户端 QoS ACK 上限。
     * <p>许可覆盖 ACK 的身份校验、原消息查询以及重试任务取消等完整异步处理链，
     * 同时也是 QoS 控制虚拟线程执行器的待处理任务上限。</p>
     */
    public static final int MAX_IN_FLIGHT = 1024;

    /**
     * 单个客户端连接允许同时处理的 QoS ACK 上限，防止单连接占满全节点控制通道。
     */
    public static final int MAX_PER_CHANNEL = 16;

    /**
     * 单条客户端 QoS ACK 处理链的超时时间，单位为秒。
     * <p>超时后结束本次控制任务并释放准入许可；未成功取消的下行消息仍可继续重发。</p>
     */
    public static final int TIMEOUT_SECONDS = 15;

    /**
     * 控制执行器过载时，全节点允许短暂等待重试的 ACK 上限。
     * <p>等待项会持有 Packet 处理任务和 Channel 引用，因此必须保持有界。</p>
     */
    public static final int MAX_PENDING_ACKS = 2048;

    /**
     * 控制执行器过载时，单个连接允许短暂等待重试的 ACK 上限。
     * <p>与全局等待上限共同限制单连接的资源占用。</p>
     */
    public static final int MAX_PENDING_ACKS_PER_CHANNEL = 32;

    /**
     * ACK 因控制执行器或准入容量不足而进入缓冲后，重新尝试调度的最大次数。
     */
    public static final int ACK_RETRY_ATTEMPTS = 20;

    /**
     * 缓冲 ACK 相邻两次调度尝试的间隔，单位为毫秒。
     */
    public static final long ACK_RETRY_DELAY_MS = 100L;

    /**
     * 落地节点向消息始发节点转发 QoS 重试取消指令时，发送失败后的最大重试次数。
     * <p>该值不包含首次发送。</p>
     */
    public static final int CANCEL_FORWARD_MAX_ATTEMPTS = 2;

    /**
     * QoS 重试取消指令转发失败后的重试间隔，单位为秒。
     */
    public static final int CANCEL_FORWARD_DELAY_SECONDS = 1;

    private QosControlConstant() {
    }
}
