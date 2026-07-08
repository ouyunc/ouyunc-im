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
        String channel) {

    public boolean isActive(int inProgressStatus) {
        return status == null || status == inProgressStatus;
    }

    /** 访客是否外渠进线（whatsapp / telegram / line）。 */
    public boolean isExternalVisitorChannel() {
        return CsDeliveryChannelHelper.isExternalVisitorRoute(this);
    }
}
