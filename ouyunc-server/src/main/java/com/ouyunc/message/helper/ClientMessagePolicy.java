package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MqArchiveRouting;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.processor.AbstractBaseBiProcessor;
import reactor.core.publisher.Mono;

/** 客户端业务入口权限；注册的服务端专用协议不能被普通登录连接调用。 */
public final class ClientMessagePolicy {
    private ClientMessagePolicy() { }

    public static boolean isAllowed(Packet packet) {
        byte type = packet.getMessageType();
        int content = packet.getMessage().getContentType();
        if (type == MessageTypeEnum.PING_PONG.getType()) {
            return content == MessageContentTypeEnum.PING_PONG_CONTENT.getType();
        }
        if (type == MessageTypeEnum.QOS_C2S_ACK.getType()) {
            return content == MessageContentTypeEnum.QOS_ACK_CONTENT.getType();
        }
        if (MqArchiveRouting.isFriendRequestType(type) || MqArchiveRouting.isGroupRequestType(type)) {
            return isClientContent(content)
                    && content != MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType()
                    && content != MessageContentTypeEnum.WITHDRAW_CONTENT.getType();
        }
        return (type == MessageTypeEnum.ONE_2_ONE.getType() || type == MessageTypeEnum.GROUP.getType()
                || type == MessageTypeEnum.CUSTOMER_SERVICE.getType()) && isClientContent(content);
    }

    /** 内置内容或明确注册给外部协议的扩展处理器才允许进入业务处理链。 */
    private static boolean isClientContent(int content) {
        if (isServerControlContent(content)) {
            return false;
        }
        if (MessageContentTypeEnum.getByType(content) != null) {
            return true;
        }
        AbstractBaseBiProcessor<Mono<Void>, ? extends Number> processor =
                MessageServerContext.messageContentProcessorCache.get(content);
        if (processor == null || processor.type() == null) {
            return false;
        }
        byte protocol = processor.type().getProtocol();
        return protocol == ProtocolTypeEnum.ZERO.getProtocol()
                || protocol == ProtocolTypeEnum.WS.getProtocol()
                || protocol == ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol();
    }

    /** 防止把服务端通知内容套进普通聊天消息，绕过通知入口限制。 */
    private static boolean isServerControlContent(int content) {
        return MqArchiveRouting.isServerNotifyOverlayContent(content)
                || content == MessageContentTypeEnum.MESSAGE_SUBMISSION_RESPONSE_CONTENT.getType()
                || content == MessageContentTypeEnum.DUPLICATE_LOGIN_CONTENT.getType()
                || content == MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType()
                || content == MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType()
                || content == MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType()
                || content == MessageContentTypeEnum.LOGIN_RESPONSE_FAIL_CONTENT.getType()
                || content == MessageContentTypeEnum.PING_PONG_CONTENT.getType()
                || content == MessageContentTypeEnum.QOS_ACK_CONTENT.getType();
    }
}
