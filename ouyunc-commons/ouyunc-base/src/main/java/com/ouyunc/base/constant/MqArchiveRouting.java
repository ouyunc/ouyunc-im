package com.ouyunc.base.constant;

import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import org.apache.commons.lang3.StringUtils;

/**
 * 确认路径路由：聊天走 SAVE；已读/撤回/好友/群只走领域 topic。
 * 归档 Kafka record key 带 appKey，避免跨租户撞分区。
 */
public final class MqArchiveRouting {

    private MqArchiveRouting() {
    }

    /**
     * 已读、撤回、好友请求、群请求只确认领域 topic，不再打 {@link MqConstant#MQ_SAVE_MESSAGE_TOPIC}。
     */
    public static boolean usesDomainConfirmOnly(Packet packet) {
        if (packet == null) {
            return false;
        }
        byte messageType = packet.getMessageType();
        if (isFriendRequestType(messageType) || isGroupRequestType(messageType)) {
            return true;
        }
        Message message = packet.getMessage();
        if (message == null) {
            return false;
        }
        int contentType = message.getContentType();
        return contentType == MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType()
                || contentType == MessageContentTypeEnum.WITHDRAW_CONTENT.getType();
    }

    public static boolean isFriendRequestType(byte messageType) {
        return messageType == MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_JOIN.getType()
                || messageType == MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_AGREE.getType()
                || messageType == MessageTypeEnum.ONE_2_ONE_FRIEND_REQUEST_REFUSE.getType();
    }

    public static boolean isGroupRequestType(byte messageType) {
        return messageType == MessageTypeEnum.GROUP_REQUEST_JOIN.getType()
                || messageType == MessageTypeEnum.GROUP_REQUEST_INVITE_JOIN.getType()
                || messageType == MessageTypeEnum.GROUP_REQUEST_AGREE.getType()
                || messageType == MessageTypeEnum.GROUP_REQUEST_REFUSE.getType()
                || messageType == MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_AGREE.getType()
                || messageType == MessageTypeEnum.GROUP_REQUEST_INVITED_JOINER_REFUSE.getType();
    }

    /**
     * SAVE / 会话类确认的分区键。
     */
    public static String partitionKey(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return null;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata() != null ? message.getMetadata().getAppKey() : null;
        String scope;
        if (packet.getMessageType() == MessageTypeEnum.CUSTOMER_SERVICE.getType()) {
            scope = StringUtils.trimToNull(message.getCorrelationId());
        } else if (packet.getMessageType() == MessageTypeEnum.GROUP.getType()
                || isGroupRequestType(packet.getMessageType())) {
            scope = StringUtils.trimToNull(message.getTo());
        } else {
            scope = IdentityUtil.sessionId(message.getFrom(), message.getTo());
        }
        if (StringUtils.isBlank(scope)) {
            return StringUtils.trimToNull(appKey);
        }
        if (StringUtils.isBlank(appKey)) {
            return scope;
        }
        return appKey + MessageConstant.COLON + scope;
    }
}
