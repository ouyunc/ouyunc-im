package com.ouyunc.message.helper;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QoS S2C ACK：确认服务端已收到客户端业务消息。
 * 优先写回入站 Channel；仅在连接已不可用时才按登录身份查表投递。
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
        Target ackTarget = buildQosAckTarget(ctx, packet, metadata, ackMessage.getFrom());
        String ackTo = ackTarget != null ? ackTarget.getTargetIdentity() : ackMessage.getFrom();
        if (StringUtils.isBlank(ackTo) && !MessageHelper.isChannelSendable(ctx)) {
            log.warn("QoS S2C ACK 无法确定接收方，跳过发送, packetId={}", packet.getPacketId());
            return;
        }

        long serverPacketId;
        String originalClientMessageId;
        if (packet.getMessageType() == MessageTypeEnum.QOS_DUP.getType()) {
            Packet dupPacket = ctx != null
                    ? ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_QOS_DUP_ORIGINAL_PACKET)
                    : null;
            if (dupPacket == null) {
                dupPacket = JSON.parseObject(packet.getMessage().getContent(), Packet.class);
            }
            if (ctx != null) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_QOS_DUP_ORIGINAL_PACKET, null);
            }
            if (dupPacket == null) {
                return;
            }
            serverPacketId = dupPacket.getPacketId();
            originalClientMessageId = dupPacket.getMessage() != null ? dupPacket.getMessage().getId() : null;
        } else {
            serverPacketId = packet.getPacketId();
            originalClientMessageId = packet.getMessage().getId();
        }

        ackMessage.setId(MessageContext.idGenerator().generateIdStr());
        ackMessage.setFrom(null);
        ackMessage.setTo(ackTo);
        ackMessage.setQos(QosLevelEnum.QOS_0.getLevel());
        ackMessage.setContent(JSON.toJSONString(new QosAckContent(
                String.valueOf(serverPacketId), originalClientMessageId)));
        ackMessage.setContentType(MessageContentTypeEnum.TEXT_CONTENT.getType());
        ackMessage.setCreateTime(TimeUtil.currentTimeMillis());
        ackPacket.setPacketId(MessageContext.idGenerator().generateId());
        ackPacket.setMessageType(MessageTypeEnum.QOS_S2C_ACK.getType());
        if (ackTarget != null && metadata != null) {
            metadata.setTarget(ackTarget);
        }

        if (MessageHelper.isChannelSendable(ctx)) {
            MessageHelper.sendOnChannel(ctx, ackPacket, sendResult -> {});
            return;
        }
        if (ackTarget == null) {
            log.warn("QoS S2C ACK 入站连接已不可用且无投递目标, packetId={}", packet.getPacketId());
            return;
        }
        MessageHelper.asyncSendMessage(ackPacket, ackTarget);
    }

    /**
     * 解析 QoS S2C ACK 投递目标：优先 Channel 登录身份，其次报文 from。
     */
    static Target buildQosAckTarget(ChannelHandlerContext ctx, Packet packet, Metadata metadata, String messageFrom) {
        LoginClientInfo loginClientInfo = ctx != null
                ? ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN)
                : null;
        if (loginClientInfo != null && StringUtils.isNotBlank(loginClientInfo.getIdentity())) {
            String appKey = StringUtils.isNotBlank(loginClientInfo.getAppKey())
                    ? loginClientInfo.getAppKey()
                    : (metadata != null ? metadata.getAppKey() : null);
            String serverAddress = StringUtils.isNotBlank(loginClientInfo.getLoginServerAddress())
                    ? loginClientInfo.getLoginServerAddress()
                    : MessageServerContext.serverProperties().getLocalServerAddress();
            return Target.newBuilder()
                    .appKey(appKey)
                    .targetIdentity(loginClientInfo.getIdentity())
                    .deviceType(loginClientInfo.getDeviceType())
                    .targetServerAddress(serverAddress)
                    .protocol(loginClientInfo.getProtocol())
                    .protocolVersion(loginClientInfo.getProtocolVersion())
                    .build();
        }
        if (StringUtils.isBlank(messageFrom) || metadata == null) {
            return null;
        }
        return Target.newBuilder()
                .appKey(metadata.getAppKey())
                .targetIdentity(messageFrom)
                .deviceType(MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType()))
                .targetServerAddress(MessageServerContext.serverProperties().getLocalServerAddress())
                .protocol(packet.getProtocol())
                .protocolVersion(packet.getProtocolVersion())
                .build();
    }
}
