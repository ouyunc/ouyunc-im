package com.ouyunc.repository.support;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.base.constant.enums.ArchiveClaimEnum;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QoS 幂等。
 * <p>客户端稳定键为 messageId；判重成功时把 packet 收敛为首次正式 packetId，再允许 ACK。</p>
 */
public final class QosRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(QosRepositorySupport.class);

    private final RepositoryInfrastructure infra;

    public QosRepositorySupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    @SuppressWarnings("unchecked")
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        // COMMITTED + 正式 packetId 才算重复；会改写 packet.packetId
        return QosIdempotencyHelper.isDuplicate(infra.redisTemplate, packet, channelLoginIdentity);
    }

    /** 恢复入口必须区分未提交与读取故障，防止故障回退到当前业务校验并误报拒绝。 */
    @SuppressWarnings("unchecked")
    public boolean checkCommittedForRecovery(Packet packet, String identity) {
        QosIdempotencyHelper.ClaimResult state = QosIdempotencyHelper.checkStateResult(
                infra.redisTemplate, packet, identity);
        if (state.state() == QosIdempotencyHelper.CLAIM_FAILED
                || state.state() == QosIdempotencyHelper.CLAIM_CONFLICT) {
            throw new IllegalStateException("QoS 历史提交身份暂不可确认");
        }
        if (!state.isCommittedWithCanonical()) {
            return false;
        }
        packet.setPacketId(state.canonicalPacketId());
        return true;
    }

    /**
     * 归档前抢占 QoS，把 packetId 收敛为首次正式 ID。已占位则不再换令牌。
     *
     * @return false 时不得归档、不得当成功
     */
    @SuppressWarnings("unchecked")
    public ArchiveClaimEnum claimForArchive(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return ArchiveClaimEnum.FAILED;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isBlank(message.getId())) {
            return ArchiveClaimEnum.FAILED;
        }
        if (StringUtils.isNotBlank(metadata.getQosClaim().getQosOwnerToken())) {
            return packet.getPacketId() > 0L ? ArchiveClaimEnum.READY : ArchiveClaimEnum.FAILED;
        }
        String ownerToken = QosIdempotencyHelper.newOwnerToken();
        long packetId = packet.getPacketId();
        String claimIdentity = QosClaimIdentities.resolve(message);
        if (packetId <= 0L || StringUtils.isBlank(claimIdentity)) {
            return ArchiveClaimEnum.FAILED;
        }
        metadata.ensureQosClaim().setQosOwnerToken(ownerToken);
        QosIdempotencyHelper.ClaimResult claim = QosIdempotencyHelper.tryClaimResult(
                infra.redisTemplate, metadata.getIngress().getAppKey(), packetId,
                claimIdentity, message.getId(), ownerToken, message, packet.getMessageType());
        if (claim.state() == QosIdempotencyHelper.CLAIM_COMMITTED) {
            if (!claim.isCommittedWithCanonical()) {
                clearQosClaimMarks(metadata);
                return ArchiveClaimEnum.FAILED;
            }
            packet.setPacketId(claim.canonicalPacketId());
            clearQosClaimMarks(metadata);
            return ArchiveClaimEnum.READY;
        }
        if (claim.state() == QosIdempotencyHelper.CLAIM_ACQUIRED) {
            if (claim.canonicalPacketId() > 0L) {
                packet.setPacketId(claim.canonicalPacketId());
            }
            return ArchiveClaimEnum.READY;
        }
        clearQosClaimMarks(metadata);
        log.warn("归档前 QoS 占位未拿到 state={} packetId={} messageId={}",
                claim.state(), packet.getPacketId(), message.getId());
        if (claim.state() == QosIdempotencyHelper.CLAIM_PENDING) {
            return ArchiveClaimEnum.PENDING;
        }
        if (claim.state() == QosIdempotencyHelper.CLAIM_CONFLICT) {
            return ArchiveClaimEnum.CONFLICT;
        }
        return ArchiveClaimEnum.FAILED;
    }

    @SuppressWarnings("unchecked")
    public void releaseQosClaim(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null) {
            return;
        }
        if (metadata.getQosClaim().isQosArchiveBound()) {
            // 冷库已按正式 packetId 发出，保留 PENDING 以便重试复用同一 ID
            return;
        }
        String ownerToken = metadata.getQosClaim().getQosOwnerToken();
        if (StringUtils.isBlank(ownerToken)) {
            // 尚未抢占或已 commit 清空：不能用 null 误调 release（会 no-op，但避免无意义调用噪音）
            return;
        }
        try {
            QosIdempotencyHelper.releaseClaim(infra.redisTemplate, metadata.getIngress().getAppKey(),
                    packet.getPacketId(), QosClaimIdentities.resolve(message),
                    message.getId(), ownerToken);
            metadata.ensureQosClaim().setQosOwnerToken(null);
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常: packetId={}", packet.getPacketId(), e);
        }
    }

    private static void clearQosClaimMarks(Metadata metadata) {
        if (metadata != null) {
            metadata.ensureQosClaim().setQosOwnerToken(null);
        }
    }
}
