package com.ouyunc.message.convert;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.LoginContent;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Packet ↔ Packet：集群透传；OUYUNC_CLIENT 入站补齐外部元数据，出站剥离内部元数据。
 */
public enum PacketPacketConverter implements PacketConverter<Packet> {
    INSTANCE;
    private static final Logger log = LoggerFactory.getLogger(PacketPacketConverter.class);

    @Override
    public Packet convertToPacket(ChannelHandlerContext ctx, Object msg) {
        if (!(msg instanceof Packet packet)) {
            return null;
        }
        Protocol channelProtocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        // 集群或未打标：原样透传（心跳 / 已包装的路由包）
        if (channelProtocol == null
                || channelProtocol.getProtocol() == NativePacketProtocol.OUYUNC.getProtocol()) {
            return packet;
        }
        if (channelProtocol.getProtocol() != NativePacketProtocol.OUYUNC_CLIENT.getProtocol()) {
            return packet;
        }
        return enrichClientIngress(ctx, packet);
    }

    @Override
    public Packet convertFromPacket(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return null;
        }
        Target target = packet.getMessage().getMetadata().getTarget();
        if (target == null) {
            return null;
        }
        // 集群节点间：保留元数据
        if (target.getProtocol() == NativePacketProtocol.OUYUNC.getProtocol()
                && target.getProtocolVersion() == NativePacketProtocol.OUYUNC.getProtocolVersion()) {
            return packet;
        }
        // 客户端原生：克隆后清空内部元数据，避免泄漏
        if (target.getProtocol() == NativePacketProtocol.OUYUNC_CLIENT.getProtocol()
                && target.getProtocolVersion() == NativePacketProtocol.OUYUNC_CLIENT.getProtocolVersion()) {
            Packet outbound = packet.clone();
            if (outbound.getMessage() != null) {
                outbound.getMessage().setMetadata(null);
            }
            return outbound;
        }
        return null;
    }

    /**
     * 对齐 {@link BinaryWebSocketFramePacketConverter}：登录取 appKey，其它消息从 Channel 登录态取。
     */
    private static Packet enrichClientIngress(ChannelHandlerContext ctx, Packet packet) {
        Message message = packet.getMessage();
        if (message == null) {
            return packet;
        }
        Metadata metadata = message.getMetadata();
        if (metadata == null) {
            metadata = new Metadata();
        }
        if (!metadata.isRouted()) {
            if (MessageTypeEnum.LOGIN.getType() == packet.getMessageType()) {
                LoginContent loginContent = JSON.parseObject(message.getContent(), LoginContent.class);
                if (loginContent == null || org.apache.commons.lang3.StringUtils.isBlank(loginContent.getAppKey())) {
                    log.error("OUYUNC_CLIENT 客户端:{} 登录内容无法解析或缺少 appKey", message.getFrom());
                    ctx.close();
                    throw new MessageException("客户端:" + message.getFrom() + " 登录内容无法解析");
                }
                metadata.setAppKey(loginContent.getAppKey());
            } else {
                LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (loginClientInfo == null) {
                    log.error("OUYUNC_CLIENT 客户端:{} 未登录，请先登录", message.getFrom());
                    ctx.close();
                    throw new MessageException("客户端:" + message.getFrom() + " 未登录，请先登录");
                }
                metadata.setAppKey(loginClientInfo.getAppKey());
            }
            metadata.setClientIp(IpUtil.getIp(ctx));
            metadata.setServerTime(TimeUtil.currentTimeMillis());
            metadata.setIngressSource(IngressSourceEnum.IM);
        }
        message.setMetadata(metadata);
        packet.setPacketId(MessageContext.idGenerator().generateId());
        return packet;
    }
}
