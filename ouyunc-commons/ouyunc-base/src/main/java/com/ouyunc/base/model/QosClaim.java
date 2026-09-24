package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * QoS 占位。只在受理节点的处理过程中使用，归档和集群转发前清空。
 */
public final class QosClaim implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 幂等键使用的发送方登录身份。客服会改写 message.from，claim 不能用改写后的值。 */
    private String qosClaimIdentity;
    /** PENDING 占位持有者。commit 和 release 必须带回，commit 成功后清空。 */
    private String qosOwnerToken;
    /** 抢占占位时的 packetId。正式 packetId 对齐后，仍用它定位占位键。 */
    private Long qosClaimPacketId;
    /** SAVE 归档已按正式 packetId 发出。此后失败不得释放占位。 */
    private boolean qosArchiveBound;

    public QosClaim copy() {
        try {
            return (QosClaim) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public void clear() {
        qosClaimIdentity = null;
        qosOwnerToken = null;
        qosClaimPacketId = null;
        qosArchiveBound = false;
    }

    public String getQosClaimIdentity() { return qosClaimIdentity; }
    public void setQosClaimIdentity(String qosClaimIdentity) { this.qosClaimIdentity = qosClaimIdentity; }
    public String getQosOwnerToken() { return qosOwnerToken; }
    public void setQosOwnerToken(String qosOwnerToken) { this.qosOwnerToken = qosOwnerToken; }
    public Long getQosClaimPacketId() { return qosClaimPacketId; }
    public void setQosClaimPacketId(Long qosClaimPacketId) { this.qosClaimPacketId = qosClaimPacketId; }
    public boolean isQosArchiveBound() { return qosArchiveBound; }
    public void setQosArchiveBound(boolean qosArchiveBound) { this.qosArchiveBound = qosArchiveBound; }
}
