package com.ouyunc.base.model;

import com.alibaba.fastjson2.JSONReader;
import com.alibaba.fastjson2.JSONWriter;
import com.alibaba.fastjson2.annotation.JSONType;
import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;

import java.io.Serial;
import java.io.Serializable;

/**
 * 消息内部上下文。状态按生命周期分成五块：
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
    /** 只替换入站事实，其余四块保持空对象。 */
    public Metadata(IngressFacts ingress) {
        this.ingress = ingress == null ? new IngressFacts() : ingress;
    }

    /** 归档快照去掉只在本机受理使用的占位令牌，申请快照保留。 */
    public void clearDeliveryClaims() {
        getQosClaim().clear();
        getHttpPushClaim().clear();
    }

    public IngressFacts getIngress() {
        if (ingress == null) {
            ingress = new IngressFacts();
        }
        return ingress;
    }

    public void setIngress(IngressFacts ingress) {
        this.ingress = ingress == null ? new IngressFacts() : ingress;
    }

    public ClusterRoute getClusterRoute() {
        if (clusterRoute == null) {
            clusterRoute = new ClusterRoute();
        }
        return clusterRoute;
    }

    public void setClusterRoute(ClusterRoute clusterRoute) {
        this.clusterRoute = clusterRoute == null ? new ClusterRoute() : clusterRoute;
    }

    public QosClaim getQosClaim() {
        if (qosClaim == null) {
            qosClaim = new QosClaim();
        }
        return qosClaim;
    }

    public void setQosClaim(QosClaim qosClaim) {
        this.qosClaim = qosClaim == null ? new QosClaim() : qosClaim;
    }

    public HttpPushClaim getHttpPushClaim() {
        if (httpPushClaim == null) {
            httpPushClaim = new HttpPushClaim();
        }
        return httpPushClaim;
    }

    public void setHttpPushClaim(HttpPushClaim httpPushClaim) {
        this.httpPushClaim = httpPushClaim == null ? new HttpPushClaim() : httpPushClaim;
    }

    public ClusterForwardModeEnum clusterForwardModeOrNone() {
        return ClusterForwardModeEnum.orNone(getClusterRoute().getClusterForwardMode());
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

    public RequestEventContext getRequestEventContext() { return requestEventContext; }
    public void setRequestEventContext(RequestEventContext requestEventContext) { this.requestEventContext = requestEventContext; }

    @Override
    public Metadata clone() {
        try {
            Metadata metadata = (Metadata) super.clone();
            metadata.ingress = getIngress().clone();
            metadata.clusterRoute = getClusterRoute().clone();
            metadata.qosClaim = getQosClaim().clone();
            metadata.httpPushClaim = getHttpPushClaim().clone();
            metadata.requestEventContext = requestEventContext == null ? null : requestEventContext.clone();
            return metadata;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    @Override
    public String toString() {
        return "Metadata{" +
                "ingress=" + getIngress() +
                ", clusterRoute=" + getClusterRoute() +
                ", qosClaim=" + getQosClaim() +
                ", httpPushClaim=" + getHttpPushClaim() +
                '}';
    }
}
