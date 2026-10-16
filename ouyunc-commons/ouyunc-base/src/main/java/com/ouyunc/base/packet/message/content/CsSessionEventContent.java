package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 客服会话事件的公共头。{@code SERVER_NOTIFY + contentType=-110}。
 * <p>只放每条事件都有的字段。分配快照、转接、排队、提示、附件审核各自用子类，不要往这里加字段。
 * 服务端不按子类反序列化，客户端按 {@code event} 读取对应字段。</p>
 */
public class CsSessionEventContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 事件名，如 {@code cs.session.assigned}。客户端按它分发。 */
    private String event;
    /** 咨询单主键。与 {@link #sessionId} 不是同一个值。 */
    private String ticketId;
    /** IM 会话 id。 */
    private String sessionId;
    /**
     * 当前接待坐席。
     * 推给访客时由场景写入；推给唯一坐席且此处为空时，用接收方补上。
     */
    private String agentId;

    public CsSessionEventContent() {
    }

    /**
     * 写入事件名，并在 ticket、session、接待坐席为空时补上。已有接待坐席不会被接收方覆盖。
     */
    public void applyDeliveryHeader(String event, String ticketId, String sessionId, String agentId) {
        if (hasText(event)) {
            this.event = event.trim();
        }
        if (!hasText(this.ticketId) && hasText(ticketId)) {
            this.ticketId = ticketId.trim();
        }
        if (!hasText(this.sessionId) && hasText(sessionId)) {
            this.sessionId = sessionId.trim();
        }
        if (!hasText(this.agentId) && hasText(agentId)) {
            this.agentId = agentId.trim();
        }
    }

    public String getEvent() {
        return event;
    }

    public void setEvent(String event) {
        this.event = normalize(event);
    }

    public String getTicketId() {
        return ticketId;
    }

    public void setTicketId(String ticketId) {
        this.ticketId = normalize(ticketId);
    }

    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = normalize(sessionId);
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = normalize(agentId);
    }

    protected static String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
