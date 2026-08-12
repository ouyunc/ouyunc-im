package com.ouyunc.repository.cs;

/**
 * IM 侧客服会话路由视图（由 Redis Hash 部分 field 组装，独立于 CS 写模型）。
 */
public record CsImSessionRoute(
        String ticketId,
        String sessionId,
        String userId,
        String serviceIdentity,
        String assigneeId,
        Integer status,
        String channel,
        /** 当前接待坐席类型：1人工 2机器人 3虚拟；历史路由可能为 null（按人工兼容） */
        Integer agentType) {

    public boolean isActive(int inProgressStatus) {
        return status == null || status == inProgressStatus;
    }

    /** 访客是否外渠进线（whatsapp / telegram / line）。 */
    public boolean isExternalVisitorChannel() {
        return CsDeliveryChannelHelper.isExternalVisitorRoute(this);
    }

    /** 当前 assignee 是否需要 IM 长连收消息（人工为 true）。 */
    public boolean assigneeRequiresImLongConnection() {
        return CsAgentType.requiresImLongConnection(agentType);
    }

    public boolean assigneeIsRobot() {
        return CsAgentType.isRobot(agentType);
    }
}
