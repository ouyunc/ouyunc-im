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
        /** 当前接待坐席类型：1人工 2机器人 3虚拟，必填 */
        Integer agentType,
        /** 改派世代，从 1 起；缺省或 &lt; 1 视为无效路由 */
        Long epoch) {

    public boolean isActive(int inProgressStatus) {
        return status != null && status == inProgressStatus;
    }

    /** 投递契约：须已分配坐席或机器人（epoch / agentType / status / assignee）。 */
    public boolean hasRequiredDeliveryFields() {
        return epoch != null
                && epoch >= 1L
                && CsAgentType.isKnown(agentType)
                && status != null
                && hasAssignee();
    }

    public boolean hasAssignee() {
        return assigneeId != null && !assigneeId.isBlank();
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
