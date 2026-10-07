package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.cs.CsImSessionRoute;

/**
 * 首次投递完成前确认发送方水位。调用方须位于业务工作线程及 CommittedDelivery 保护范围内。
 * Redis 水位更新具备单调性；失败抛出后同一 messageId 重试可以安全补齐，不能独立 subscribe 后忽略结果。
 */
public final class SenderReadCompletionSupport {
    private SenderReadCompletionSupport() {}

    public static void complete(Packet packet, IdentityType type) {
        if (!DefaultRepository.INSTANCE.advanceSenderReadOffsetOnSend(packet, type,
                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)) {
            throw new IllegalStateException("发送方水位尚未确认: packetId=" + packet.getPacketId());
        }
    }

    public static void completeCs(Packet packet, CsImSessionRoute route) {
        if (!DefaultRepository.INSTANCE.advanceCsSenderReadOffsetOnSend(packet, route,
                MessageConstant.CACHE_MESSAGE_READ_RECEIPT_KEY_EXPIRE_TIMESTAMP)) {
            throw new IllegalStateException("客服发送方水位尚未确认: packetId=" + packet.getPacketId());
        }
    }
}
