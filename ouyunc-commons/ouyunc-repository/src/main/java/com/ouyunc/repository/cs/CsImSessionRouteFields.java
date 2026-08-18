package com.ouyunc.repository.cs;

import java.util.List;

/**
 * IM 读取 CS 写入的 Redis Hash field 名（与 CS {@code com.ouyunc.cs.api.route.CsSessionRouteHashFields} 对齐）。
 * <p>IM 只依赖本类列出的 field；CS 新增其它 Hash field 时 IM 无需改动。</p>
 */
public final class CsImSessionRouteFields {

    public static final String TICKET_ID = "ticketId";
    public static final String SESSION_ID = "sessionId";
    public static final String USER_ID = "userId";
    public static final String SERVICE_IDENTITY = "serviceIdentity";
    public static final String ASSIGNEE_ID = "assigneeId";
    public static final String STATUS = "status";
    /** 进线渠道实例编码，与 CS ticket.channel 对齐（whatsapp_a / im） */
    public static final String CHANNEL = "channel";
    /** 进线协议，与 CS ticket.channelType 对齐（whatsapp / telegram / line / im） */
    public static final String CHANNEL_TYPE = "channelType";
    /** 坐席类型，与 CS {@code CsSessionRouteHashFields.AGENT_TYPE} 对齐 */
    public static final String AGENT_TYPE = "agentType";

    /**
     * 路由世代：CS 每次改派/绑定 HINCRBY，从 1 起。IM 读不到合法 epoch 则拒收。
     */
    public static final String EPOCH = "epoch";

    /** IM 消息投递所需 field（与 CS 写入的 Hash field 名对齐）。 */
    public static final List<String> READ_FIELDS =
            List.of(
                    TICKET_ID,
                    SESSION_ID,
                    USER_ID,
                    SERVICE_IDENTITY,
                    ASSIGNEE_ID,
                    STATUS,
                    CHANNEL,
                    CHANNEL_TYPE,
                    AGENT_TYPE,
                    EPOCH);

    /** 投递前热校验：只读会变的字段，避免整 Hash 二次 HMGET。 */
    public static final List<String> DELIVERY_FIELDS =
            List.of(ASSIGNEE_ID, EPOCH, AGENT_TYPE, STATUS);

    private CsImSessionRouteFields() {}
}
