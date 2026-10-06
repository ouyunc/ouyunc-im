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
    /** SAVE 归档已按正式 packetId 发出。此后失败不得释放占位。 */
    private boolean qosArchiveBound;
    /** 可信身份建立后、业务改写前固定的原始请求指纹；聊天和控制消息的幂等校验统一使用。 */
    private String qosPayloadHash;

    @Override
    public QosClaim clone() {
        try {
            return (QosClaim) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public void clear() {
        qosClaimIdentity = null;
        qosOwnerToken = null;
        qosArchiveBound = false;
        qosPayloadHash = null;
    }

    public String getQosClaimIdentity() { return qosClaimIdentity; }
    public void setQosClaimIdentity(String qosClaimIdentity) { this.qosClaimIdentity = qosClaimIdentity; }
    public String getQosOwnerToken() { return qosOwnerToken; }
    public void setQosOwnerToken(String qosOwnerToken) { this.qosOwnerToken = qosOwnerToken; }
    public boolean isQosArchiveBound() { return qosArchiveBound; }
    public void setQosArchiveBound(boolean qosArchiveBound) { this.qosArchiveBound = qosArchiveBound; }
    public String getQosPayloadHash() { return qosPayloadHash; }
    public void setQosPayloadHash(String qosPayloadHash) { this.qosPayloadHash = qosPayloadHash; }

    @Override
    public String toString() {
        return "QosClaim{" +
                "qosClaimIdentity='" + qosClaimIdentity + '\'' +
                ", qosOwnerToken='" + qosOwnerToken + '\'' +
                ", qosArchiveBound=" + qosArchiveBound +
                '}';
    }
}
