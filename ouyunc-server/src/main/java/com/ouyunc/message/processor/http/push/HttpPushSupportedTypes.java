package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.PushTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.http.HttpPipelineException;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.lang3.StringUtils;

import java.util.EnumSet;
import java.util.HashSet;
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

    /** 业务卡片 contentType（单聊/群/客服共用）。 */
    private static final Set<Integer> BUSINESS_CARD_CONTENT_TYPES = Set.of(
            MessageContentTypeEnum.LOCATION_CONTENT.getType(),
            MessageContentTypeEnum.POSTCARD_CONTENT.getType(),
            MessageContentTypeEnum.PRODUCT_CARD_CONTENT.getType(),
            MessageContentTypeEnum.ORDER_CARD_CONTENT.getType(),
            MessageContentTypeEnum.LOGISTICS_CARD_CONTENT.getType(),
            MessageContentTypeEnum.PROFILE_CARD_CONTENT.getType(),
            MessageContentTypeEnum.RECOMMEND_ITEM_CONTENT.getType()
    );

    /** 单聊/群 HTTP 推送：文本 + 业务卡片。 */
    private static final Set<Integer> ONE2ONE_GROUP_SUPPORTED_CONTENT_TYPES;

    /** 客服 HTTP 推送允许的聊天/控制 contentType（与 WS 对齐）。 */
    private static final Set<Integer> CS_SUPPORTED_CONTENT_TYPES;

    static {
        Set<Integer> one2oneGroup = new HashSet<>();
        one2oneGroup.add(MessageContentTypeEnum.TEXT_CONTENT.getType());
        one2oneGroup.addAll(BUSINESS_CARD_CONTENT_TYPES);
        one2oneGroup.add(MessageContentTypeEnum.WITHDRAW_CONTENT.getType());
        one2oneGroup.add(MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType());
        ONE2ONE_GROUP_SUPPORTED_CONTENT_TYPES = Set.copyOf(one2oneGroup);

        Set<Integer> cs = new HashSet<>();
        cs.add(MessageContentTypeEnum.TEXT_CONTENT.getType());
        cs.add(MessageContentTypeEnum.IMAGE_CONTENT.getType());
        cs.add(MessageContentTypeEnum.FILE_CONTENT.getType());
        cs.add(MessageContentTypeEnum.VOICE_CONTENT.getType());
        cs.add(MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType());
        cs.add(MessageContentTypeEnum.VIDEO_CONTENT.getType());
        cs.add(MessageContentTypeEnum.VOICE_CALL_CONTENT.getType());
        cs.add(MessageContentTypeEnum.VIDEO_CALL_CONTENT.getType());
        cs.addAll(BUSINESS_CARD_CONTENT_TYPES);
        cs.add(MessageContentTypeEnum.WITHDRAW_CONTENT.getType());
        cs.add(MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType());
        CS_SUPPORTED_CONTENT_TYPES = Set.copyOf(cs);
    }

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

    public static boolean isSupportedOne2OneOrGroupContent(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        return ONE2ONE_GROUP_SUPPORTED_CONTENT_TYPES.contains(packet.getMessage().getContentType());
    }

    public static boolean isSupportedCsContent(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        return CS_SUPPORTED_CONTENT_TYPES.contains(packet.getMessage().getContentType());
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
        byte messageType = packet.getMessageType();
        if (messageType == MessageTypeEnum.CUSTOMER_SERVICE.getType()) {
            if (StringUtils.isBlank(packet.getMessage().getCorrelationId())) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                        "客服推送必须传 correlationId（ticketId）");
            }
            if (!isSupportedCsContent(packet)) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                        "HTTP 推送客服不支持 contentType=" + packet.getMessage().getContentType());
            }
            return;
        }
        if (messageType == MessageTypeEnum.ONE_2_ONE.getType()
                || messageType == MessageTypeEnum.GROUP.getType()) {
            if (!isSupportedOne2OneOrGroupContent(packet)) {
                throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                        "HTTP 推送单聊/群不支持 contentType=" + packet.getMessage().getContentType()
                                + "，仅支持文本、业务卡片、撤回与已读回执");
            }
            return;
        }
        int contentType = packet.getMessage().getContentType();
        if (contentType == MessageContentTypeEnum.TEXT_CONTENT.getType()
                || contentType == MessageContentTypeEnum.TRANSLATION_READY_CONTENT.getType()) {
            return;
        }
        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                "HTTP 推送服务端通知仅支持文本或译文就绪 contentType=" + contentType);
    }
}
