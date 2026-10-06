package com.ouyunc.message.processor;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.helper.RequestCompletionHelper;
import com.ouyunc.message.helper.QosCommittedDeliverySupport;
import com.ouyunc.message.helper.QosRequestFingerprintSupport;

/** 好友/群请求的 COMMITTED 仅表示热写完成；受理前还必须补齐领域命令与通知。 */
public abstract class AbstractRequestMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    @Override
    public boolean qosPreHandle(io.netty.channel.ChannelHandlerContext ctx, Packet packet) {
        // 已完成的身份校验绑定了真实 from；此时固定原始请求指纹，MASK 后仍沿用同一个幂等身份。
        QosRequestFingerprintSupport.captureIfAbsent(packet);
        return QosCommittedDeliverySupport.handle(ctx, packet,
                com.ouyunc.repository.DefaultRepository.INSTANCE, RequestCompletionHelper::complete);
    }
}
