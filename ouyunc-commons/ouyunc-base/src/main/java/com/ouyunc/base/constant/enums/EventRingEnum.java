package com.ouyunc.base.constant.enums;

import com.lmax.disruptor.dsl.ProducerType;

/**
 * Disruptor RingBuffer 性能等级
 * 如需增加自定义 RingBuffer，直接在此枚举中新增即可
 */
public enum EventRingEnum {

    /** 高优先级、延迟敏感的事件（登录、支付等核心链路） */
    FAST(65536, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 普通业务事件（默认） */
    NORMAL(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 耗时较长的事件（日志、统计、持久化等） */
    SLOW(262144, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 客户端登录事件（YIELDING 在少核机器上无事件时会空转占满 CPU，生产统一 BLOCKING） */
    CLIENT_LOGIN(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 客户端登出事件 */
    CLIENT_LOGOUT(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 移除离线消息事件 */
    REMOVE_OFFLINE_MESSAGE(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 发送失败消息事件 */
    SEND_FAIL_MESSAGE(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 客户端登录保活刷新事件专用 RingBuffer（与登录/登出链路隔离） */
    CLIENT_KEEP_ALIVE_REFRESH(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    /** 客户端业务会话空闲（CLIENT_BUSINESS_SESSION_IDLE）专用 RingBuffer，与其它事件隔离 */
    CLIENT_BUSINESS_SESSION_IDLE(1048576, WaitStrategyMode.BLOCKING, ProducerType.MULTI),

    ;
    /**
     * RingBuffer 大小（必须是 2 的幂）
     */
    private final int bufferSize;

    /**
     * 等待策略模式（具体策略由事件多播器映射为 Disruptor WaitStrategy）
     */
    private final WaitStrategyMode waitStrategyMode;

    /**
     * 生产者模式（由事件多播器映射为 Disruptor ProducerType）
     */
    private final ProducerType producerType;

    EventRingEnum(int bufferSize, WaitStrategyMode waitStrategyMode, ProducerType producerType) {
        this.bufferSize = bufferSize;
        this.waitStrategyMode = waitStrategyMode;
        this.producerType = producerType;
    }

    public int getBufferSize() {
        return bufferSize;
    }

    public WaitStrategyMode getWaitStrategyMode() {
        return waitStrategyMode;
    }

    public ProducerType getProducerType() {
        return producerType;
    }

    public enum WaitStrategyMode {
        BLOCKING,
        BUSY_SPIN_WAIT,
        YIELDING
    }
}
