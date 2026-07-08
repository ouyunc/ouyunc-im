package com.ouyunc.message.processor.http.push;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.model.MessagePushExtra;
import com.ouyunc.base.model.MessagePushRequest;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.util.Map;

/**
 * 将 HTTP {@link MessagePushRequest} 转为内线 {@link Packet}。
 */
public final class MessagePushPacketConverter {

    private MessagePushPacketConverter() {
    }

    public static Packet convert(MessagePushRequest request, HttpContext httpContext) throws HttpPipelineException {
        int fromType = resolveFromType(httpContext);
        int toTypeHint = request.getToType() != null
                ? request.getToType() : MessageFromToTypeEnum.USER.getType();
        ResolvedPushType resolved = resolvePushType(request, fromType, toTypeHint);
        int toType = request.getToType() != null
                ? request.getToType() : defaultToType(resolved.messageType());
        String appKey = httpContext.getAppKey();
        long now = request.getCreateTime() != null && request.getCreateTime() > 0
                ? request.getCreateTime() : TimeUtil.currentTimeMillis();

        Metadata metadata = new Metadata(appKey, resolveClientIp(httpContext), now);
        metadata.setIngressSource(IngressSourceEnum.HTTP_PUSH.getCode());
        metadata.setHttpPushType(request.getPushType());

        Message message = buildMessage(request, resolved, metadata, now, fromType, toType, httpContext);
        return new Packet(
                ProtocolTypeEnum.HTTP.getProtocol(),
                ProtocolTypeEnum.HTTP.getProtocolVersion(),
                MessageContext.idGenerator().generateId(),
                NumberConstant.NUMBER_0,
                NetworkEnum.OTHER.getValue(),
                NumberConstant.NUMBER_0,
                Serializer.JSON.getValue(),
                resolved.messageType().getType(),
                message
        );
    }

    private static Message buildMessage(MessagePushRequest request, ResolvedPushType resolved,
                                        Metadata metadata, long createTime,
                                        int fromType, int toType, HttpContext httpContext) throws HttpPipelineException {
        String from = resolveFrom(httpContext);
        String to = StringUtils.trimToNull(request.getTo());
        if (to == null && resolved.pushTypeEnum() != PushTypeEnum.BROADCAST_SERVER_NOTIFY) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "缺少接收方 to");
        }

        String content = normalizeContent(request.getContent(), resolved.messageType());
        Message message = new Message(
                request.getMessageId(),
                from,
                to != null ? to : MessageConstant.SPLAT,
                resolved.contentType().getType(),
                content,
                createTime,
                metadata
        );
        message.setFromType(fromType);
        message.setToType(toType);

        MessagePushExtra extra = request.getExtra();
        if (extra != null) {
            if (extra.getAt() != null) {
                message.setAt(extra.getAt());
            }
            if (extra.getRef() != null) {
                message.setRef(extra.getRef());
            }
            if (extra.getQos() != null) {
                message.setQos(extra.getQos());
            }
            Map<String, Object> extensions = extra.getExtensions();
            if (extensions != null && !extensions.isEmpty()) {
                ChannelMessagePushSupport.applyExtensions(metadata, extensions);
                message.setExtra(JSON.toJSONString(extensions));
            }
        }
        return message;
    }

    private static String normalizeContent(String rawContent, MessageType messageType) {
        if (StringUtils.isBlank(rawContent)) {
            return rawContent;
        }
        if (messageType == MessageTypeEnum.SERVER_NOTIFY && !rawContent.trim().startsWith("{")) {
            return Serializer.JSON.serializeToString(new ServerNotifyContent(rawContent));
        }
        return rawContent;
    }

    private static int defaultToType(MessageType messageType) {
        if (messageType == MessageTypeEnum.GROUP) {
            return MessageFromToTypeEnum.GROUP.getType();
        }
        return MessageFromToTypeEnum.USER.getType();
    }

    private static String resolveFrom(HttpContext httpContext) throws HttpPipelineException {
        HttpAuthPrincipal principal = httpContext.getAuthPrincipal();
        if (principal == null || StringUtils.isBlank(principal.getIdentity())) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "缺少 JWT 发送方身份");
        }
        return principal.getIdentity();
    }

    /**
     * HTTP 推送的发送方类型仅来自 JWT（见 {@link HttpPushJwtAuth}），与用户表 {@code type}、登录 scope 无关联。
     */
    private static int resolveFromType(HttpContext httpContext) throws HttpPipelineException {
        HttpAuthPrincipal principal = httpContext.getAuthPrincipal();
        if (principal == null || StringUtils.isBlank(principal.getIdentity())) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "缺少 JWT 发送方身份");
        }
        if (principal.getFromType() == null) {
            throw new HttpPipelineException(HttpResponseStatus.UNAUTHORIZED, HttpResponseCodeEnum.UNAUTHORIZED,
                    "JWT 缺少 fromType claim");
        }
        return principal.getFromType();
    }

    private static ResolvedPushType resolvePushType(MessagePushRequest request, int fromType, int toType)
            throws HttpPipelineException {
        PushTypeEnum pushTypeEnum = request.getPushType() != null
                ? PushTypeEnum.getPushTypeEnum(request.getPushType()) : null;
        if (pushTypeEnum != null) {
            if (!HttpPushSupportedTypes.isSupportedPushType(pushTypeEnum)) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                        "不支持的 pushType=" + request.getPushType());
            }
            return new ResolvedPushType(pushTypeEnum, pushTypeEnum.getMessageType(), pushTypeEnum.getMessageContentType());
        }
        MessageType derived = deriveMessageType(fromType, toType);
        if (!HttpPushSupportedTypes.isSupportedMessageType(derived)) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "无法解析 pushType，请显式指定 pushType（0-4：系统通知/私聊/群聊/客服/广播）");
        }
        return new ResolvedPushType(null, derived, MessageContentTypeEnum.TEXT_CONTENT);
    }

    private static MessageType deriveMessageType(int fromType, int toType) {
        if (toType == MessageFromToTypeEnum.GROUP.getType()) {
            return MessageTypeEnum.GROUP;
        }
        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType() || fromType == MessageFromToTypeEnum.CS_VISITOR.getType()
                || toType == MessageFromToTypeEnum.CS_AGENT.getType() || toType == MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return MessageTypeEnum.CUSTOMER_SERVICE;
        }
        if (fromType == MessageFromToTypeEnum.USER.getType() && toType == MessageFromToTypeEnum.USER.getType()) {
            return MessageTypeEnum.ONE_2_ONE;
        }
        if (fromType == MessageFromToTypeEnum.SYSTEM.getType() || fromType == MessageFromToTypeEnum.BOT.getType()) {
            return MessageTypeEnum.SERVER_NOTIFY;
        }
        return MessageTypeEnum.ONE_2_ONE;
    }

    private static String resolveClientIp(HttpContext httpContext) {
        if (httpContext.getRequest() == null || httpContext.getRequest().headers() == null) {
            return null;
        }
        String forwarded = httpContext.getRequest().headers().get("X-Forwarded-For");
        if (StringUtils.isNotBlank(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        if (httpContext.getChannelContext() != null && httpContext.getChannelContext().channel() != null
                && httpContext.getChannelContext().channel().remoteAddress() != null) {
            Channel channel = httpContext.getChannelContext().channel();
            SocketAddress socketAddr = channel.remoteAddress();
            if (socketAddr instanceof InetSocketAddress) {
                return ((InetSocketAddress) socketAddr).getHostString();
            }
            // 兜底处理 /ip:port 字符串
            String raw = socketAddr.toString();
            raw = raw.startsWith("/") ? raw.substring(1) : raw;
            return raw.split(":")[0];
        }
        return null;
    }


    private record ResolvedPushType(PushTypeEnum pushTypeEnum, MessageType messageType, MessageContentType contentType) {
    }
}
