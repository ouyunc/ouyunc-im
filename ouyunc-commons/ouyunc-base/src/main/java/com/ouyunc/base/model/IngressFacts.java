package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.ModerationModeEnum;
import com.ouyunc.base.constant.enums.ModerationStatusEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 入站后不再改写的事实：租户、来源、首次到达节点和审核结论。
 */
public final class IngressFacts implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 租户标识。 */
    private String appKey;
    /** 客户端真实 IP，入站时由服务端写入。 */
    private String clientIp;
    /** 消息首次到达服务端的时间戳，毫秒。 */
    private long serverTime;
    /** 首次入站节点 ip:port。集群中转不得改写，持久化为 server_address。 */
    private String originServerAddress;
    /** 入口来源：长连接、HTTP 推送或外部渠道回调。 */
    private IngressSourceEnum ingressSource;
    /** HTTP 推送的 pushType 原值，用于区分广播等场景。长连接为 null。 */
    private Integer httpPushType;
    /** 内容审核状态。未审核为 null。 */
    private ModerationStatusEnum moderationStatus;
    /** 内容审核模式。 */
    private ModerationModeEnum moderationMode;

    public IngressFacts() {
    }

    public IngressFacts(String appKey, String clientIp, String originServerAddress, long serverTime) {
        this.appKey = appKey;
        this.clientIp = clientIp;
        this.originServerAddress = originServerAddress;
        this.serverTime = serverTime;
    }

    public IngressFacts copy() {
        try {
            return (IngressFacts) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public String getAppKey() { return appKey; }
    public void setAppKey(String appKey) { this.appKey = appKey; }
    public String getClientIp() { return clientIp; }
    public void setClientIp(String clientIp) { this.clientIp = clientIp; }
    public long getServerTime() { return serverTime; }
    public void setServerTime(long serverTime) { this.serverTime = serverTime; }
    public String getOriginServerAddress() { return originServerAddress; }
    public void setOriginServerAddress(String originServerAddress) { this.originServerAddress = originServerAddress; }
    public IngressSourceEnum getIngressSource() { return ingressSource; }
    public void setIngressSource(IngressSourceEnum ingressSource) { this.ingressSource = ingressSource; }
    public Integer getHttpPushType() { return httpPushType; }
    public void setHttpPushType(Integer httpPushType) { this.httpPushType = httpPushType; }
    public ModerationStatusEnum getModerationStatus() { return moderationStatus; }
    public void setModerationStatus(ModerationStatusEnum moderationStatus) { this.moderationStatus = moderationStatus; }
    public ModerationModeEnum getModerationMode() { return moderationMode; }
    public void setModerationMode(ModerationModeEnum moderationMode) { this.moderationMode = moderationMode; }
}
