package com.ouyunc.repository.cs;

import com.ouyunc.base.constant.CacheConstant;
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
     * 这条消息要加到哪一侧未读。访客消息进坐席水位，坐席/机器人消息进访客水位。
     * 转接通知等系统消息返回 null，不记未读。
     */
    public static String incomingUnreadSide(Message message, CsImSessionRoute route) {
        if (isVisitorAuthor(message, route)) {
            return CacheConstant.CS_TICKET_UNREAD_STAFF;
        }
        if (isStaffAuthor(message, route)) {
            return CacheConstant.CS_TICKET_UNREAD_VISITOR;
        }
        return null;
    }

    /**
     * 作者已读/回复要推进的水位。任一坐席或机器人都推进 staff，访客推进 visitor。
     */
    public static String authorReadSide(Message message, CsImSessionRoute route) {
        if (isVisitorAuthor(message, route)) {
            return CacheConstant.CS_TICKET_UNREAD_VISITOR;
        }
        if (isStaffAuthor(message, route)) {
            return CacheConstant.CS_TICKET_UNREAD_STAFF;
        }
        return null;
    }

    /** 坐席或机器人。先看 fromType，避免和访客 id 撞车。 */
    public static boolean isStaffAuthor(Message message, CsImSessionRoute route) {
        if (message == null || route == null) {
            return false;
        }
        int fromType = message.getFromType();
        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()
                || fromType == MessageFromToTypeEnum.BOT.getType()) {
            return true;
        }
        return StringUtils.isNotBlank(route.assigneeId())
                && StringUtils.equals(message.getFrom(), route.assigneeId());
    }

    public static boolean isVisitorAuthor(Message message, CsImSessionRoute route) {
        if (message == null || route == null || isStaffAuthor(message, route)) {
            return false;
        }
        if (message.getFromType() == MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return true;
        }
        return StringUtils.isNotBlank(route.userId())
                && StringUtils.equals(message.getFrom(), route.userId());
    }
}
