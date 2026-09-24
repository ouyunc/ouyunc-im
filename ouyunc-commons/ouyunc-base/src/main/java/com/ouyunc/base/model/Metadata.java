package com.ouyunc.base.model;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.ModerationModeEnum;
import com.ouyunc.base.constant.enums.ModerationStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;

/**
 * 消息内部上下文。对外仍是一组取值方法，状态按生命周期分开放：
 * 入站事实、集群路由、QoS 占位、HTTP 幂等、申请快照。
 */
@JSONType(serializeFeatures = JSONWriter.Feature.FieldBased, deserializeFeatures = JSONReader.Feature.FieldBased)
public class Metadata implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 入站事实：租户、来源、首次到达节点、审核结论。 */
    private IngressFacts ingress = new IngressFacts();
    /** 集群路由：下一跳、路由表、扇出。只在节点之间使用。 */
    private ClusterRoute clusterRoute = new ClusterRoute();
    /** QoS 占位令牌。只在本机受理过程中使用，写入 Kafka 前清空。 */
    private QosClaim qosClaim = new QosClaim();
    /** HTTP 推送幂等令牌。只在 HTTP 入口使用，写入 Kafka 前清空。 */
    private HttpPushClaim httpPushClaim = new HttpPushClaim();
    /** 好友/群申请快照。普通聊天为 null。 */
    private RequestEventContext requestEventContext;

    public Metadata() {
    }

    /**
     * 只接收已组装好的五块。某一块传 null 时用空对象，不在这里拆字段赋值。
     */
    public Metadata(IngressFacts ingress, ClusterRoute clusterRoute, QosClaim qosClaim,
                    HttpPushClaim httpPushClaim, RequestEventContext requestEventContext) {
        this.ingress = ingress == null ? new IngressFacts() : ingress;
        this.clusterRoute = clusterRoute == null ? new ClusterRoute() : clusterRoute;
        this.qosClaim = qosClaim == null ? new QosClaim() : qosClaim;
        this.httpPushClaim = httpPushClaim == null ? new HttpPushClaim() : httpPushClaim;
        this.requestEventContext = requestEventContext;
    }
    /**
     * 只接收已组装好的五块。某一块传 null 时用空对象，不在这里拆字段赋值。
     */
    public Metadata(IngressFacts ingress) {
        this.ingress = ingress == null ? new IngressFacts() : ingress;
    }

    /** 归档快照去掉只在本机受理使用的占位令牌，申请快照保留。 */
    public void clearDeliveryClaims() {
        qosClaim().clear();
        httpPushClaim().clear();
    }

    private IngressFacts ingress() {
        if (ingress == null) {
            ingress = new IngressFacts();
        }
        return ingress;
    }

    private ClusterRoute clusterRoute() {
        if (clusterRoute == null) {
            clusterRoute = new ClusterRoute();
        }
        return clusterRoute;
    }

    private QosClaim qosClaim() {
        if (qosClaim == null) {
            qosClaim = new QosClaim();
        }
        return qosClaim;
    }

    private HttpPushClaim httpPushClaim() {
        if (httpPushClaim == null) {
            httpPushClaim = new HttpPushClaim();
        }
        return httpPushClaim;
    }

    public String getAppKey() { return ingress().getAppKey(); }
    public void setAppKey(String appKey) { ingress().setAppKey(appKey); }
    public Target getTarget() { return clusterRoute().getTarget(); }
    public void setTarget(Target target) { clusterRoute().setTarget(target); }
    public int getCurrentRetry() { return clusterRoute().getCurrentRetry(); }
    public void setCurrentRetry(int currentRetry) { clusterRoute().setCurrentRetry(currentRetry); }

    public List<RoutingTable> getRoutingTables() {
        return clusterRoute().routingTables();
    }

    public void setRoutingTables(List<RoutingTable> routingTables) {
        clusterRoute().setRoutingTables(routingTables);
    }

    public ClusterForwardModeEnum getClusterForwardMode() { return clusterRoute().getClusterForwardMode(); }
    public void setClusterForwardMode(ClusterForwardModeEnum clusterForwardMode) {
        clusterRoute().setClusterForwardMode(clusterForwardMode);
    }

    public ClusterForwardModeEnum clusterForwardModeOrNone() {
        return ClusterForwardModeEnum.orNone(getClusterForwardMode());
    }

    public boolean isLocalIngress() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.NONE;
    }

    public boolean isClientForward() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.CLIENT;
    }

    public boolean isInternalForward() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.INTERNAL;
    }

    public String getFromServerAddress() { return clusterRoute().getFromServerAddress(); }
    public void setFromServerAddress(String fromServerAddress) { clusterRoute().setFromServerAddress(fromServerAddress); }
    public long getServerTime() { return ingress().getServerTime(); }
    public void setServerTime(long serverTime) { ingress().setServerTime(serverTime); }
    public String getClientIp() { return ingress().getClientIp(); }
    public void setClientIp(String clientIp) { ingress().setClientIp(clientIp); }
    public IngressSourceEnum getIngressSource() { return ingress().getIngressSource(); }
    public void setIngressSource(IngressSourceEnum ingressSource) { ingress().setIngressSource(ingressSource); }

    public Integer getHttpPushType() { return ingress().getHttpPushType(); }
    public void setHttpPushType(Integer httpPushType) { ingress().setHttpPushType(httpPushType); }
    public String getQosClaimIdentity() { return qosClaim().getQosClaimIdentity(); }
    public void setQosClaimIdentity(String qosClaimIdentity) { qosClaim().setQosClaimIdentity(qosClaimIdentity); }
    public String getQosOwnerToken() { return qosClaim().getQosOwnerToken(); }
    public void setQosOwnerToken(String qosOwnerToken) { qosClaim().setQosOwnerToken(qosOwnerToken); }
    public Long getQosClaimPacketId() { return qosClaim().getQosClaimPacketId(); }
    public void setQosClaimPacketId(Long qosClaimPacketId) { qosClaim().setQosClaimPacketId(qosClaimPacketId); }
    public boolean isQosArchiveBound() { return qosClaim().isQosArchiveBound(); }
    public void setQosArchiveBound(boolean qosArchiveBound) { qosClaim().setQosArchiveBound(qosArchiveBound); }
    public String getHttpPushOwnerToken() { return httpPushClaim().getHttpPushOwnerToken(); }
    public void setHttpPushOwnerToken(String httpPushOwnerToken) { httpPushClaim().setHttpPushOwnerToken(httpPushOwnerToken); }
    public String getHttpPushPayloadHash() { return httpPushClaim().getHttpPushPayloadHash(); }
    public void setHttpPushPayloadHash(String httpPushPayloadHash) { httpPushClaim().setHttpPushPayloadHash(httpPushPayloadHash); }
    public String getOriginServerAddress() { return ingress().getOriginServerAddress(); }
    public void setOriginServerAddress(String originServerAddress) { ingress().setOriginServerAddress(originServerAddress); }
    public int getLoginFollowHops() { return clusterRoute().getLoginFollowHops(); }
    public void setLoginFollowHops(int loginFollowHops) { clusterRoute().setLoginFollowHops(loginFollowHops); }
    public ModerationStatusEnum getModerationStatus() { return ingress().getModerationStatus(); }
    public void setModerationStatus(ModerationStatusEnum moderationStatus) { ingress().setModerationStatus(moderationStatus); }
    public ModerationModeEnum getModerationMode() { return ingress().getModerationMode(); }
    public void setModerationMode(ModerationModeEnum moderationMode) { ingress().setModerationMode(moderationMode); }
    public boolean isLocalBroadcastOnly() { return clusterRoute().isLocalBroadcastOnly(); }
    public void setLocalBroadcastOnly(boolean localBroadcastOnly) { clusterRoute().setLocalBroadcastOnly(localBroadcastOnly); }
    public List<Target> getFanoutTargets() { return clusterRoute().getFanoutTargets(); }
    public void setFanoutTargets(List<Target> fanoutTargets) { clusterRoute().setFanoutTargets(fanoutTargets); }
    public RequestEventContext getRequestEventContext() { return requestEventContext; }
    public void setRequestEventContext(RequestEventContext requestEventContext) { this.requestEventContext = requestEventContext; }

    @Override
    public Metadata clone() {
        try {
            Metadata metadata = (Metadata) super.clone();
            metadata.ingress = ingress().copy();
            metadata.clusterRoute = clusterRoute().copy();
            metadata.qosClaim = qosClaim().copy();
            metadata.httpPushClaim = httpPushClaim().copy();
            metadata.requestEventContext = requestEventContext == null ? null : requestEventContext.clone();
            return metadata;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "Metadata{" +
                "appKey='" + getAppKey() + '\'' +
                ", clusterForwardMode=" + getClusterForwardMode() +
                ", currentRetry=" + getCurrentRetry() +
                ", fromServerAddress='" + getFromServerAddress() + '\'' +
                ", target=" + getTarget() +
                ", clientIp='" + getClientIp() + '\'' +
                ", serverTime=" + getServerTime() +
                ", ingressSource=" + getIngressSource() +
                ", httpPushType=" + getHttpPushType() +
                ", qosClaimIdentity='" + getQosClaimIdentity() + '\'' +
                ", originServerAddress='" + getOriginServerAddress() + '\'' +
                ", loginFollowHops=" + getLoginFollowHops() +
                ", moderationStatus=" + getModerationStatus() +
                ", moderationMode=" + getModerationMode() +
                '}';
    }
}
