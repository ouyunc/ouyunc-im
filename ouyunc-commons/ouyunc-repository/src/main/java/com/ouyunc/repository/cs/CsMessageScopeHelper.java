package com.ouyunc.repository.cs;

import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * 客服消息双 scope 工具：channelSessionId（路由）与 ticketMessageScopeId（消息索引）。
 */
public final class CsMessageScopeHelper {

    private CsMessageScopeHelper() {
    }

    public static String ticketMessageScopeId(CsImSessionRoute route) {
        return route != null && route.ticketId() != null ? route.ticketId().trim() : null;
    }

    /**
     * 已读 offset / 未读归属的真实用户 id（坐席用 assigneeId，访客用 userId）。
     */
    public static String resolveReaderId(Message message, CsImSessionRoute route) {
        if (message == null || route == null) {
            return null;
        }
        int fromType = message.getFromType();
        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()
                || StringUtils.equals(message.getFrom(), route.serviceIdentity())) {
            return route.assigneeId();
        }
        if (fromType == MessageFromToTypeEnum.CS_VISITOR.getType()
                || StringUtils.equals(message.getFrom(), route.userId())) {
            return route.userId();
        }
        return message.getFrom();
    }
}
