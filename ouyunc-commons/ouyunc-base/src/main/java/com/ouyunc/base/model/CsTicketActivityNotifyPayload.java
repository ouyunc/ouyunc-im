package com.ouyunc.base.model;

import com.ouyunc.base.constant.MessageConstant;
import java.io.Serial;
import java.io.Serializable;

/**
 * CS ticket 活动 MQ 载荷（topic {@code ouyunc-cs-ticket-activity}），
 * 与 CS 侧 {@code CsTicketActivityNotifyRequest} JSON 字段一致。
 */
public class CsTicketActivityNotifyPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appKey;
    private Long ticketId;
    private Long packetId;
    private Integer fromType;
    private Long serverTimeMs;

    /**
     * 当前接待坐席类型：1人工 2机器人 3虚拟（与路由 Hash agentType 对齐）。
     * <p>历史消息可能为空。</p>
     */
    private Integer agentType;

    public CsTicketActivityNotifyPayload() {
    }

    public CsTicketActivityNotifyPayload(
            String appKey, Long ticketId, Long packetId, Integer fromType, Long serverTimeMs) {
        this(appKey, ticketId, packetId, fromType, serverTimeMs, null);
    }

    public CsTicketActivityNotifyPayload(
            String appKey,
            Long ticketId,
            Long packetId,
            Integer fromType,
            Long serverTimeMs,
            Integer agentType) {
        this.appKey = appKey;
        this.ticketId = ticketId;
        this.packetId = packetId;
        this.fromType = fromType;
        this.serverTimeMs = serverTimeMs;
        this.agentType = agentType;
    }

    public static String messageKey(String appKey, long ticketId) {
        return appKey + MessageConstant.COLON + ticketId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Long getTicketId() {
        return ticketId;
    }

    public void setTicketId(Long ticketId) {
        this.ticketId = ticketId;
    }

    public Long getPacketId() {
        return packetId;
    }

    public void setPacketId(Long packetId) {
        this.packetId = packetId;
    }

    public Integer getFromType() {
        return fromType;
    }

    public void setFromType(Integer fromType) {
        this.fromType = fromType;
    }

    public Long getServerTimeMs() {
        return serverTimeMs;
    }

    public void setServerTimeMs(Long serverTimeMs) {
        this.serverTimeMs = serverTimeMs;
    }

    public Integer getAgentType() {
        return agentType;
    }

    public void setAgentType(Integer agentType) {
        this.agentType = agentType;
    }
}
