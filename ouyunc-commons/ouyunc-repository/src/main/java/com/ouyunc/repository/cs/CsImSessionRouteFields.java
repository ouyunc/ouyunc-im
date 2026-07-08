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

    /** IM 消息投递所需 field 顺序（与 {@link #multiGetFieldOrder()} 一致）。 */
    public static final List<String> READ_FIELDS =
            List.of(TICKET_ID, SESSION_ID, USER_ID, SERVICE_IDENTITY, ASSIGNEE_ID, STATUS);

    private CsImSessionRouteFields() {}
}
