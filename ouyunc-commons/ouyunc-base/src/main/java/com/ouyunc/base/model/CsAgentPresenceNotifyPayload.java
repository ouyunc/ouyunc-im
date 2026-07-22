package com.ouyunc.base.model;

import com.ouyunc.base.constant.MessageConstant;

import java.io.Serial;
import java.io.Serializable;

/**
 * CS 坐席 IM 通道 presence MQ 载荷（topic {@code ouyunc-cs-agent-presence}），
 * 与 CS 侧 {@code CsAgentPresenceNotifyRequest} JSON 字段一致。
 */
public class CsAgentPresenceNotifyPayload implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String appKey;
    private String agentId;
    private Integer scope;
    private Byte deviceType;
    private String reason;
    private Long serverTimeMs;
    /** {@link MessageConstant#CS_AGENT_PRESENCE_CHANNEL_OPEN} / {@link MessageConstant#CS_AGENT_PRESENCE_CHANNEL_CLOSE} */
    private String eventType;

    public CsAgentPresenceNotifyPayload() {
    }

    public CsAgentPresenceNotifyPayload(
            String appKey,
            String agentId,
            Integer scope,
            Byte deviceType,
            String reason,
            Long serverTimeMs,
            String eventType) {
        this.appKey = appKey;
        this.agentId = agentId;
        this.scope = scope;
        this.deviceType = deviceType;
        this.reason = reason;
        this.serverTimeMs = serverTimeMs;
        this.eventType = eventType;
    }

    public static String messageKey(String appKey, String agentId) {
        return appKey + MessageConstant.COLON + agentId;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getAgentId() {
        return agentId;
    }

    public void setAgentId(String agentId) {
        this.agentId = agentId;
    }

    public Integer getScope() {
        return scope;
    }

    public void setScope(Integer scope) {
        this.scope = scope;
    }

    public Byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(Byte deviceType) {
        this.deviceType = deviceType;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getServerTimeMs() {
        return serverTimeMs;
    }

    public void setServerTimeMs(Long serverTimeMs) {
        this.serverTimeMs = serverTimeMs;
    }

    public String getEventType() {
        return eventType;
    }

    public void setEventType(String eventType) {
        this.eventType = eventType;
    }
}
