package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpPipelineException;
import io.netty.handler.codec.http.HttpResponseStatus;

import java.util.EnumSet;
import java.util.Set;

/**
 * HTTP 推送支持的消息类型与 pushType 白名单。
 */
public final class HttpPushSupportedTypes {

    private static final Set<MessageTypeEnum> SUPPORTED_MESSAGE_TYPES = EnumSet.of(
            MessageTypeEnum.ONE_2_ONE,
            MessageTypeEnum.GROUP,
            MessageTypeEnum.SERVER_NOTIFY,
            MessageTypeEnum.CUSTOMER_SERVICE
    );

    private static final Set<PushTypeEnum> SUPPORTED_PUSH_TYPES = EnumSet.allOf(PushTypeEnum.class);

    private HttpPushSupportedTypes() {
    }

    public static boolean isSupportedMessageType(byte messageType) {
        for (MessageTypeEnum type : SUPPORTED_MESSAGE_TYPES) {
            if (type.getType() == messageType) {
                return true;
            }
        }
        return false;
    }

    public static boolean isSupportedMessageType(MessageType messageType) {
        return messageType instanceof MessageTypeEnum type && SUPPORTED_MESSAGE_TYPES.contains(type);
    }

    public static boolean isSupportedPushType(PushTypeEnum pushType) {
        return pushType != null && SUPPORTED_PUSH_TYPES.contains(pushType);
    }

    public static boolean isSupportedTextContent(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        return packet.getMessage().getContentType() == MessageContentTypeEnum.TEXT_CONTENT.getType();
    }

    public static void validate(Packet packet) throws HttpPipelineException {
        if (packet == null) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "HTTP 推送 Packet 不能为空");
        }
        if (!isSupportedMessageType(packet.getMessageType())) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "HTTP 推送不支持的消息类型 messageType=" + packet.getMessageType()
                            + "，仅支持：一对一、群聊、服务端通知、客服");
        }
        if (!isSupportedTextContent(packet)) {
            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                    "HTTP 推送暂仅支持文本消息 contentType=" + (packet.getMessage() != null
                            ? packet.getMessage().getContentType() : null));
        }
    }
}
