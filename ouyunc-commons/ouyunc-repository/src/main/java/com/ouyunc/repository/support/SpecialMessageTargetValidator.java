package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;

/**
 * 已读 / 撤回等操作所引用目标消息的通用校验。
 */
final class SpecialMessageTargetValidator {

    private SpecialMessageTargetValidator() {
    }

    /**
     * 已读回执与撤回仅允许指向可展示聊天消息，排除协议控制类与特殊消息。
     */
    static boolean isChatTargetMessage(Packet targetPacket) {
        if (targetPacket == null || targetPacket.getMessage() == null) {
            return false;
        }
        return isChatTargetContentType(targetPacket.getMessage().getContentType());
    }

    static boolean isChatTargetContentType(int contentType) {
        if (contentType == MessageContentTypeEnum.WITHDRAW_CONTENT.getType()
                || contentType == MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType()) {
            return false;
        }
        return contentType != MessageContentTypeEnum.PING_PONG_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_RESPONSE_FAIL_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType()
                && contentType != MessageContentTypeEnum.QOS_DUP_CONTENT.getType()
                && contentType != MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType();
    }
}
