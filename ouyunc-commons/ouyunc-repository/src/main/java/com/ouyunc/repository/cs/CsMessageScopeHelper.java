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
     * 这条消息要记进谁的收件箱。访客消息进入口 id，坐席/机器人消息进访客登录 id。
     * 与私聊一样按收件人分槽；对端是 ticketId，不按坐席、不按设备拆。
     * 转接通知等系统消息返回 null。
     */
    public static String incomingUnreadOwner(Message message, CsImSessionRoute route) {
        if (isVisitorAuthor(message, route)) {
            return StringUtils.trimToNull(route.serviceIdentity());
        }
        if (isStaffAuthor(message, route)) {
            return StringUtils.trimToNull(route.userId());
        }
        return null;
    }

    /**
     * 已读或回复要推进谁的水位。访客推进自己的登录 id，任一坐席或机器人都推进入口 id。
     * 转接只换 assignee，入口 id 不变，所以水位行不增加。
     */
    public static String authorReadOwner(Message message, CsImSessionRoute route) {
        if (isVisitorAuthor(message, route)) {
            return StringUtils.trimToNull(route.userId());
        }
        if (isStaffAuthor(message, route)) {
            return StringUtils.trimToNull(route.serviceIdentity());
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
