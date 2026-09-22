package com.ouyunc.message.safety;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.PacketChannelWriter;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入站内容安全：在连接有序任务线程上检查，与业务 process 同序。
 * <p>不是 Netty Handler，禁止挂到 EventLoop 管道。</p>
 */
public final class ContentSafetyIngress {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyIngress.class);

    private ContentSafetyIngress() {
    }

    /**
     * 敏感词检查；REJECT 时回写并不再进入业务。检查异常放行，避免误杀。PING 不要调用。
     *
     * @param ctx    通道上下文
     * @param packet 协议包
     * @return {@code true} 继续业务；{@code false} 已拒绝
     */
    public static boolean applyOnWorker(ChannelHandlerContext ctx, Packet packet) {
        ContentSafetyResult result;
        try {
            result = ContentSafetyFacade.check(packet);
        } catch (Exception e) {
            log.error("内容安全检查异常，放行以免误杀 packetId={}", packet == null ? null : packet.getPacketId(), e);
            return true;
        }
        if (result != null && !result.isPassed()) {
            log.warn("内容安全拒绝 packetId={} reason={} hits={}",
                    packet.getPacketId(), result.getReason(), result.getHitWords());
            ExceptionReporter.reportBusiness(ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT, ExceptionCodeEnum.CONTENT_SENSITIVE_REJECT.getMessage(), "ContentSafetyIngress.applyOnWorker", packet);
            replyReject(ctx, packet);
            return false;
        }
        return true;
    }

    /** REJECT 回写 SERVER_NOTIFY。 */
    private static void replyReject(ChannelHandlerContext ctx, Packet source) {
        PacketChannelWriter.tryReplyOnChannel(ctx, buildRejectNotify(ctx, source));
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
