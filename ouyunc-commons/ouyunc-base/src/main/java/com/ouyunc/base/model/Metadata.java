package com.ouyunc.base.model;


import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.IngressSourceEnum;
import com.ouyunc.base.constant.enums.ModerationModeEnum;
import com.ouyunc.base.constant.enums.ModerationStatusEnum;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * @Author fzx
 * @Description: 扩展消息 message, 额外字段数据，内部使用，不对外开放,集群使用
 **/
public class Metadata implements Serializable, Cloneable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 平台唯一标识
     */
    private String appKey;

    /**
     * 集群转发意图：落地写客户端还是进集群 Processor。null 视为 {@link ClusterForwardModeEnum#NONE}。
     */
    private ClusterForwardModeEnum clusterForwardMode;

    /**
     * 当前消息投递重试次数,默认0
     */
    private int currentRetry;

     /**
      * 消息发送者所在服务器地址,如果是消息在服务之间转发，则该地址是上个消息所经过的服务地址：ip:port
      */
     private String fromServerAddress;

    /**
     * 发送消息的目标信息（最终接收方）。{@code targetServerAddress} 是落地机，集群中转不得改写为下一跳。
     */
    private Target target;

    /**
     * 消息路由表
     */
    private List<RoutingTable> routingTables;

    /**
     * 消息客户端真实ip
     */
    private String clientIp;

    /**
     * 消息首次到达服务时间戳
     */
    private long serverTime;

    /**
     * 消息入口来源。
     */
    private IngressSourceEnum ingressSource;

    /**
     * HTTP 推送时的 pushType 原值，便于 Processor 区分广播等场景。
     */
    private Integer httpPushType;

    /**
     * QoS 幂等 client 键使用的发送方登录身份。
     * 客服会把 {@code message.from} 改写成入口 serviceIdentity，claim/release 不能用改写后的 from。
     */
    private String qosClaimIdentity;

    /**
     * QoS PENDING 占位持有者令牌。落库抢占成功后写入，失败释放/commit 必须带回；
     * commit 成功后应清空。仅服务端内部使用，勿下发给客户端。
     */
    private String qosOwnerToken;

    /**
     * 抢占 PENDING 时用于构造幂等键的 packetId。接管重发会把 {@code packet.packetId} 对齐成首次的
     * canonical ID，但占位记录仍写在本字段对应的键上，commit/release 必须按本字段定位，否则找不到占位。
     * 仅服务端内部使用，勿下发给客户端。
     */
    private Long qosClaimPacketId;

    /**
     * SAVE 归档已按当前正式 packetId 发出。此后失败不得释放 PENDING，避免重试换新 ID 与冷库首条分叉。
     * 仅服务端内部使用。
     */
    private boolean qosArchiveBound;

    /** HTTP 推送幂等占位的执行令牌，仅允许当前持有者提交、失败标记或释放。 */
    private String httpPushOwnerToken;

    /** HTTP 推送规范化后的请求指纹，用于阻止同 messageId 替换业务正文。 */
    private String httpPushPayloadHash;

    /**
     * 首次外部入站节点（ip:port），与 clientIp 同阶段由服务端赋值，持久化为 server_address。集群中转只改 {@link #fromServerAddress}，本字段禁止改写。
     * 仅服务端内部使用，写出给客户端前必须剥离 metadata。
     */
    private String originServerAddress;

    /**
     * 落地本机无连接时，按登录 HASH 再投到新节点的次数。不改 {@link #originServerAddress}。
     */
    private int loginFollowHops;

    /**
     * 内容审核状态。
     */
    private ModerationStatusEnum moderationStatus;

    /**
     * 内容审核模式。
     */
    private ModerationModeEnum moderationMode;


    /**
     * 集群广播：仅投递本机连接，不再向其他节点扇出。
     */
    private boolean localBroadcastOnly;

    /**
     * 跨节点聚合扇出：落地节点按此列表本机展开投递；正文只传一份。
     * 单目标投递时保持 null；含私人 Target 字段，禁止跨用户复用同一 Packet 实例写不同连接前不 setTarget。
     */
    private List<Target> fanoutTargets;


    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public Target getTarget() {
        return target;
    }

    public void setTarget(Target target) {
        this.target = target;
    }

    public int getCurrentRetry() {
        return currentRetry;
    }

    public void setCurrentRetry(int currentRetry) {
        this.currentRetry = currentRetry;
    }

    public List<RoutingTable> getRoutingTables() {
        if (routingTables == null) {
            routingTables = new ArrayList<>();
        }
        return routingTables;
    }

    public void setRoutingTables(List<RoutingTable> routingTables) {
        this.routingTables = routingTables;
    }

    public ClusterForwardModeEnum getClusterForwardMode() {
        return clusterForwardMode;
    }

    public void setClusterForwardMode(ClusterForwardModeEnum clusterForwardMode) {
        this.clusterForwardMode = clusterForwardMode;
    }

    public ClusterForwardModeEnum clusterForwardModeOrNone() {
        return ClusterForwardModeEnum.orNone(clusterForwardMode);
    }

    /** 尚未集群转发，按本机入站补元数据。 */
    public boolean isLocalIngress() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.NONE;
    }

    /** 落地写客户端。 */
    public boolean isClientForward() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.CLIENT;
    }

    /** 落地进集群 Processor，不写客户端。 */
    public boolean isInternalForward() {
        return clusterForwardModeOrNone() == ClusterForwardModeEnum.INTERNAL;
    }

    public String getFromServerAddress() {
        return fromServerAddress;
    }

    public void setFromServerAddress(String fromServerAddress) {
        this.fromServerAddress = fromServerAddress;
    }

    public long getServerTime() {
        return serverTime;
    }

    public void setServerTime(long serverTime) {
        this.serverTime = serverTime;
    }

    public String getClientIp() {
        return clientIp;
    }

    public void setClientIp(String clientIp) {
        this.clientIp = clientIp;
    }

    /**
     * @return 入口来源枚举
     */
    public IngressSourceEnum getIngressSource() {
        return ingressSource;
    }

    /**
     * @param ingressSource 入口来源枚举
     */
    public void setIngressSource(IngressSourceEnum ingressSource) {
        this.ingressSource = ingressSource;
    }

    /**
     * 兼容扩展字段里的短码字符串；无法识别则忽略。
     *
     * @param ingressSourceCode {@link IngressSourceEnum#getCode()} 或枚举名
     */
    public void setIngressSourceCode(String ingressSourceCode) {
        this.ingressSource = IngressSourceEnum.fromCode(ingressSourceCode);
    }

    public Integer getHttpPushType() {
        return httpPushType;
    }

    public void setHttpPushType(Integer httpPushType) {
        this.httpPushType = httpPushType;
    }

    public String getQosClaimIdentity() {
        return qosClaimIdentity;
    }

    public void setQosClaimIdentity(String qosClaimIdentity) {
        this.qosClaimIdentity = qosClaimIdentity;
    }

    public String getQosOwnerToken() {
        return qosOwnerToken;
    }

    public void setQosOwnerToken(String qosOwnerToken) {
        this.qosOwnerToken = qosOwnerToken;
    }

    public Long getQosClaimPacketId() {
        return qosClaimPacketId;
    }

    public void setQosClaimPacketId(Long qosClaimPacketId) {
        this.qosClaimPacketId = qosClaimPacketId;
    }

    public boolean isQosArchiveBound() {
        return qosArchiveBound;
    }

    public void setQosArchiveBound(boolean qosArchiveBound) {
        this.qosArchiveBound = qosArchiveBound;
    }

    public String getHttpPushOwnerToken() {
        return httpPushOwnerToken;
    }

    public void setHttpPushOwnerToken(String httpPushOwnerToken) {
        this.httpPushOwnerToken = httpPushOwnerToken;
    }

    public String getHttpPushPayloadHash() {
        return httpPushPayloadHash;
    }

    public void setHttpPushPayloadHash(String httpPushPayloadHash) {
        this.httpPushPayloadHash = httpPushPayloadHash;
    }

    public String getOriginServerAddress() {
        return originServerAddress;
    }

    public void setOriginServerAddress(String originServerAddress) {
        this.originServerAddress = originServerAddress;
    }

    public int getLoginFollowHops() {
        return loginFollowHops;
    }

    public void setLoginFollowHops(int loginFollowHops) {
        this.loginFollowHops = loginFollowHops;
    }

    /**
     * @return 内容审核状态
     */
    public ModerationStatusEnum getModerationStatus() {
        return moderationStatus;
    }

    /**
     * @param moderationStatus 内容审核状态
     */
    public void setModerationStatus(ModerationStatusEnum moderationStatus) {
        this.moderationStatus = moderationStatus;
    }

    /**
     * @return 审核模式
     */
    public ModerationModeEnum getModerationMode() {
        return moderationMode;
    }

    /**
     * @param moderationMode 审核模式
     */
    public void setModerationMode(ModerationModeEnum moderationMode) {
        this.moderationMode = moderationMode;
    }

    public boolean isLocalBroadcastOnly() {
        return localBroadcastOnly;
    }

    public void setLocalBroadcastOnly(boolean localBroadcastOnly) {
        this.localBroadcastOnly = localBroadcastOnly;
    }

    public List<Target> getFanoutTargets() {
        return fanoutTargets;
    }

    public void setFanoutTargets(List<Target> fanoutTargets) {
        this.fanoutTargets = fanoutTargets;
    }

    public Metadata(String appKey, ClusterForwardModeEnum clusterForwardMode, int currentRetry, String fromServerAddress, Target target, List<RoutingTable> routingTables, String clientIp, long serverTime) {
        this.appKey = appKey;
        this.clusterForwardMode = clusterForwardMode;
        this.currentRetry = currentRetry;
        this.fromServerAddress = fromServerAddress;
        this.target = target;
        this.routingTables = routingTables;
        this.clientIp = clientIp;
        this.serverTime = serverTime;
    }

    public Metadata(String appKey, String clientIp, long serverTime) {
        this.appKey = appKey;
        this.clientIp = clientIp;
        this.serverTime = serverTime;
    }

    public Metadata(String appKey, String clientIp, String originServerAddress, long serverTime) {
        this.appKey = appKey;
        this.clientIp = clientIp;
        this.originServerAddress = originServerAddress;
        this.serverTime = serverTime;
    }

    public Metadata() {
    }

    @Override
    public Metadata clone() {
        try {
            Metadata metadata = (Metadata) super.clone();
            if (this.target != null) {
                metadata.setTarget(this.target.clone());
            }
            if (this.routingTables != null) {
                List<RoutingTable> routingTableList = new ArrayList<>();
                for (RoutingTable routingTable : this.routingTables) {
                    routingTableList.add(routingTable.clone());
                }
                metadata.setRoutingTables(routingTableList);
            }
            if (this.fanoutTargets != null) {
                List<Target> copied = new ArrayList<>(this.fanoutTargets.size());
                for (Target t : this.fanoutTargets) {
                    copied.add(t == null ? null : t.clone());
                }
                metadata.setFanoutTargets(copied);
            }
            return metadata;
        } catch (CloneNotSupportedException e) {
            throw new AssertionError();
        }
    }

    @Override
    public String toString() {
        return "Metadata{" +
                "appKey='" + appKey + '\'' +
                ", clusterForwardMode=" + clusterForwardMode +
                ", currentRetry=" + currentRetry +
                ", fromServerAddress='" + fromServerAddress + '\'' +
                ", target=" + target +
                ", routingTables=" + routingTables +
                ", clientIp='" + clientIp + '\'' +
                ", serverTime=" + serverTime +
                ", ingressSource=" + ingressSource +
                ", httpPushType=" + httpPushType +
                ", qosClaimIdentity='" + qosClaimIdentity + '\'' +
                ", originServerAddress='" + originServerAddress + '\'' +
                ", loginFollowHops=" + loginFollowHops +
                ", moderationStatus=" + moderationStatus +
                ", moderationMode=" + moderationMode +
                '}';
    }
}
