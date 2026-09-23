package com.ouyunc.message.helper;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageSendStatusEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.MessageSendResultContent;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 长连接逐消息受理结果。优先写原入站连接，避免 Redis/MQ 故障时再依赖故障组件查路由。
 * 写回失败由客户端以稳定 messageId 超时核对；结果包本身为 QoS0，禁止进入业务归档。
 */
public final class MessageSendResultHelper {
    private static final Logger log = LoggerFactory.getLogger(MessageSendResultHelper.class);
    private static final ObjectMapper JSON = new ObjectMapper();

    private MessageSendResultHelper() { }

    public static void accepted(ChannelHandlerContext ctx, Packet source) {
        send(ctx, source, MessageSendStatusEnum.ACCEPTED, null, null);
    }

    public static void rejected(ChannelHandlerContext ctx, Packet source, ExceptionCodeEnum code) {
        send(ctx, source, MessageSendStatusEnum.REJECTED, code, null);
    }

    public static void retryLater(ChannelHandlerContext ctx, Packet source, ExceptionCodeEnum code) {
        send(ctx, source, MessageSendStatusEnum.RETRY_LATER, code, null);
    }

    public static void unknown(ChannelHandlerContext ctx, Packet source, ExceptionCodeEnum code) {
        send(ctx, source, MessageSendStatusEnum.UNKNOWN, code, null);
    }

    /**
     * 结果始终携带原 messageId。非成功结果不暴露尚未提交的临时 packetId。
     * 只有连接仍可用时才发送；连接断开后的最终状态由同 ID 重试核对。
     */
    private static void send(ChannelHandlerContext ctx, Packet source, MessageSendStatusEnum status,
                             ExceptionCodeEnum code, Long retryAfterMs) {
        if (source == null || source.getMessage() == null || ctx == null) {
            return;
        }
        Message original = source.getMessage();
        if (StringUtils.isBlank(original.getId())) {
            log.warn("发送结果缺少原 messageId，packetId={}", source.getPacketId());
            return;
        }
        Packet result = source.clone();
        Message reply = result.getMessage();
        Metadata metadata = reply.getMetadata();
        Target target = PacketChannelWriter.resolveReplyTarget(ctx, source, original.getFrom());
        reply.setId(MessageContext.idGenerator().generateIdStr());
        reply.setFrom(null);
        reply.setTo(target != null ? target.getTargetIdentity() : original.getFrom());
        reply.setQos(QosLevelEnum.QOS_0.getLevel());
        try {
            reply.setContent(JSON.writeValueAsString(new MessageSendResultContent(original.getId(),
                    status == MessageSendStatusEnum.ACCEPTED ? String.valueOf(source.getPacketId()) : null,
                    status.name(), code == null ? null : code.getCode(),
                    code == null ? null : code.getMessage(), retryAfterMs)));
        } catch (JsonProcessingException error) {
            log.error("发送结果序列化失败, messageId={}", original.getId(), error);
            return;
        }
        reply.setContentType(MessageContentTypeEnum.MESSAGE_SEND_RESULT_CONTENT.getType());
        reply.setCreateTime(TimeUtil.currentTimeMillis());
        result.setPacketId(MessageContext.idGenerator().generateId());
        result.setMessageType(MessageTypeEnum.MESSAGE_SEND_RESULT.getType());
        if (metadata != null && target != null) {
            metadata.setTarget(target);
        }
        if (!source.markSendResultOnce()) {
            log.debug("消息受理结果已经回送, messageId={}", original.getId());
            return;
        }
        if (ctx.channel() == null || !ctx.channel().isActive()) {
            log.debug("发送结果未写回：入站连接已关闭，messageId={} status={}", original.getId(), status);
            return;
        }
        // 结果包无需再触发 SEND_FAIL 业务事件，客户端负责同 messageId 超时核对。
        PacketChannelWriter.sendOnChannelBestEffort(ctx, result);
    }
}
