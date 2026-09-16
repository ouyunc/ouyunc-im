package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.helper.PacketChannelWriter;
import com.ouyunc.message.protocol.NativePacketProtocol;
import com.ouyunc.message.safety.ContentSafetyFacade;
import com.ouyunc.base.constant.MessageConstant;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.mqtt.MqttFixedHeader;
import io.netty.handler.codec.mqtt.MqttMessageFactory;
import io.netty.handler.codec.mqtt.MqttMessageType;
import io.netty.handler.codec.mqtt.MqttPublishMessage;
import io.netty.handler.codec.mqtt.MqttPublishVariableHeader;
import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.util.CharsetUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内容安全 Netty 处理器。
 * <p>须挂在登录鉴权之后、业务 {@code PacketHandler} 之前。REJECT 不向下传递并发 40010；MASK/AUDIT/PASS 继续 fire。
 * 检查异常时放行，避免误杀。</p>
 */
public class ContentSafetyHandler extends SimpleChannelInboundHandler<Packet> {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyHandler.class);

    /**
     * 对入站 Packet 做敏感词检查。
     *
     * @param ctx    通道上下文
     * @param packet 协议包
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            log.error("内容安全检查异常，放行以免误杀 packetId={}", packet == null ? null : packet.getPacketId(), e);
            ctx.fireChannelRead(packet);
            return;
        }
        if (result != null && !result.isPassed()) {
            log.warn("内容安全拒绝 packetId={} reason={} hits={}",
                    packet.getPacketId(), result.getReason(), result.getHitWords());
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT,
                            ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            replyReject(ctx, packet);
            return;
        }
        ctx.fireChannelRead(packet);
    }

    /**
     * REJECT 回写：MQTT 发 QoS0 PUBLISH；其它协议发 SERVER_NOTIFY。
     */
    private static void replyReject(ChannelHandlerContext ctx, Packet source) {
        if (isMqtt(ctx, source)) {
            replyMqttReject(ctx, source);
            return;
        }
        PacketChannelWriter.tryReplyOnChannel(ctx, buildRejectNotify(ctx, source));
    }

    private static boolean isMqtt(ChannelHandlerContext ctx, Packet source) {
        if (source != null && source.getProtocol() == NativePacketProtocol.MQTT.getProtocol()) {
            return true;
        }
        Protocol protocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        return protocol != null && protocol.getProtocol() == NativePacketProtocol.MQTT.getProtocol();
    }

    private static void replyMqttReject(ChannelHandlerContext ctx, Packet source) {
        try {
            String text = ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage();
            MqttPublishMessage publish = (MqttPublishMessage) MqttMessageFactory.newMessage(
                    new MqttFixedHeader(MqttMessageType.PUBLISH, false, MqttQoS.AT_MOST_ONCE, false, 0),
                    new MqttPublishVariableHeader(MessageConstant.MQTT_SYS_NOTIFY_TOPIC, 0),
                    Unpooled.copiedBuffer(text, CharsetUtil.UTF_8));
            MessageHelper.tryWriteObject(ctx.channel(), publish, source, sendResult -> {});
        } catch (Exception e) {
            log.warn("MQTT 内容安全拒绝回写失败 channelId={}", ctx.channel().id().asShortText(), e);
        }
    }
    private static Packet buildRejectNotify(ChannelHandlerContext ctx, Packet source) {
        long now = TimeUtil.currentTimeMillis();
        LoginClientInfo loginInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        Metadata metadata = new Metadata();
        if (source.getMessage() != null && source.getMessage().getMetadata() != null) {
            metadata.setAppKey(source.getMessage().getMetadata().getAppKey());
        } else if (loginInfo != null) {
            metadata.setAppKey(loginInfo.getAppKey());
        }
        metadata.setServerTime(now);
        String to = loginInfo != null ? loginInfo.getIdentity()
                : (source.getMessage() != null ? source.getMessage().getFrom() : null);
        byte protocol = loginInfo != null ? loginInfo.getProtocol() : source.getProtocol();
        byte protocolVersion = loginInfo != null ? loginInfo.getProtocolVersion() : source.getProtocolVersion();
        byte deviceType = loginInfo != null ? loginInfo.getDeviceType() : source.getDeviceType();
        Message notify = new Message(
                MessageContext.idGenerator().generateIdStr(),
                null,
                to,
                MessageContentTypeEnum.TEXT_CONTENT.getType(),
                Serializer.JSON.serializeToString(new ServerNotifyContent(
                        ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage())),
                now,
                metadata);
        return new Packet(
                protocol,
                protocolVersion,
                MessageContext.idGenerator().generateId(),
                deviceType,
                NetworkEnum.OTHER.getValue(),
                Encrypt.SymmetryEncrypt.NONE.getValue(),
                Serializer.JSON.getValue(),
                MessageTypeEnum.SERVER_NOTIFY.getType(),
                notify);
    }
}
