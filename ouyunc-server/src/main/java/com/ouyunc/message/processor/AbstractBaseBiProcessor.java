package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.processor.BiProcessor;
import com.ouyunc.core.qos.Qos;
import com.ouyunc.message.helper.MessageSubmissionResponseHelper;
import com.ouyunc.message.helper.QosCommittedDeliverySupport;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;
import io.netty.channel.ChannelHandlerContext;

/**
 * 基础抽象处理类。
 *
 * @param <R> {@link #process} 返回值类型（消息侧多为 {@code Mono<Void>}）
 * @param <T> {@link #type()} 协议类型数值（Byte/Integer 等）
 */
public abstract class AbstractBaseBiProcessor<R, T extends Number>
        implements BiProcessor<ChannelHandlerContext, Packet, R>, Qos {
    /**
     * 类型
     */
    public abstract ProtocolType<? extends T> type();

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository> R repository() {
        return (R) DefaultRepository.INSTANCE;
    }

    /**
     * QoS 前置判重。客户端重试以稳定 {@code messageId} 为准，packetId 可变。
     * 仅 {@code COMMITTED} 且拿到正式 packetId 时可截住主链；会将 packet 收敛到该正式 ID，
     * 先幂等补派生索引，再保证首次扇出已完成，然后 ACK。补失败或扇出未完成回 UNKNOWN。
     * {@code PENDING} 表示占位但未确认落库，必须继续处理。
     */
    @Override
    public boolean qosPreHandle(ChannelHandlerContext ctx, Packet packet) {
        return QosCommittedDeliverySupport.handle(ctx, packet, repository(), null);
    }

    /**
     * qos 后置处理，一般用于发送ack，给发送端确认消息已经到达服务端
     */
    @Override
    public void qosPostHandle(ChannelHandlerContext ctx, Packet packet) {
        MessageSubmissionResponseHelper.accepted(ctx, packet);
    }
}
