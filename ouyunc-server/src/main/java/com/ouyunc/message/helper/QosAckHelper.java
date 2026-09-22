package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QoS S2C ACK：仅确认服务端已收到客户端业务消息（该条 qos>0）。
 * ACK 包自身 qos=0，避免控制包再走业务 QoS。
 * <p>正文同时回 {@code ackId}(正式 packetId) 与 {@code messageId}；客户端停重试以 messageId 为准。
 * 优先写回入站 Channel；仅在连接已不可用时才按登录身份查表投递。</p>
 */
public final class QosAckHelper {

    private static final Logger log = LoggerFactory.getLogger(QosAckHelper.class);

    private QosAckHelper() {
    }

    public static void sendS2cAck(ChannelHandlerContext ctx, Packet packet) {
        if (!MessageContext.isQosEnable() || packet == null || packet.getMessage() == null) {
            return;
        }
        if (packet.getMessage().getQos() <= QosLevelEnum.QOS_0.getLevel()) {
            return;
        }
        Packet ackPacket = packet.clone();
        Message ackMessage = ackPacket.getMessage();
        Metadata metadata = ackMessage.getMetadata();
        // 优先 Channel 登录身份，避免 CS 入口号改写 from 后 ACK 投错人
        Target ackTarget = PacketChannelWriter.resolveReplyTarget(ctx, packet, ackMessage.getFrom());
        String ackTo = ackTarget != null ? ackTarget.getTargetIdentity() : ackMessage.getFrom();
        if (StringUtils.isBlank(ackTo) && !PacketChannelWriter.isSendable(ctx)) {
            log.warn("QoS S2C ACK 无法确定接收方，跳过发送, packetId={}", packet.getPacketId());
            return;
        }

        ackMessage.setId(MessageContext.idGenerator().generateIdStr());
        ackMessage.setFrom(null);
        ackMessage.setTo(ackTo);
        ackMessage.setQos(QosLevelEnum.QOS_0.getLevel());
        ackMessage.setContent(JSON.toJSONString(new QosAckContent(
                String.valueOf(packet.getPacketId()), packet.getMessage().getId())));
        ackMessage.setContentType(MessageContentTypeEnum.QOS_ACK_CONTENT.getType());
        ackMessage.setCreateTime(TimeUtil.currentTimeMillis());
        ackPacket.setPacketId(MessageContext.idGenerator().generateId());
        ackPacket.setMessageType(MessageTypeEnum.QOS_S2C_ACK.getType());
        if (ackTarget != null && metadata != null) {
            metadata.setTarget(ackTarget);
        }

        if (PacketChannelWriter.tryReplyOnChannel(ctx, ackPacket)) {
            return;
        }
        if (ackTarget == null) {
            log.warn("QoS S2C ACK 入站连接已不可用且无投递目标, packetId={}", packet.getPacketId());
            return;
        }
        MessageHelper.asyncSendMessage(ackPacket, ackTarget);
    }
}
