package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.QosClaimIdentities;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * QoS 幂等。
 */
public final class QosRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(QosRepositorySupport.class);

    private final RepositoryInfrastructure infra;

    public QosRepositorySupport(RepositoryInfrastructure infra) {
        this.infra = infra;
    }

    @SuppressWarnings("unchecked")
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        // 键存在性与正文哈希一致才由 COMMITTED 决定，未抢占过的消息自然不判重
        return QosIdempotencyHelper.isDuplicate(infra.redisTemplate, packet, channelLoginIdentity);
    }

    @SuppressWarnings("unchecked")
    public void releaseQosClaim(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || !MessageContext.isQosEnable()
                || message.getQos() <= QosLevelEnum.QOS_0.getLevel()) {
            return;
        }
        String ownerToken = metadata.getQosOwnerToken();
        if (StringUtils.isBlank(ownerToken)) {
            // 尚未抢占或已 commit 清空：不能用 null 误调 release（会 no-op，但避免无意义调用噪音）
            return;
        }
        try {
            QosIdempotencyHelper.releaseClaim(infra.redisTemplate, metadata.getAppKey(),
                    packet.getPacketId(), QosClaimIdentities.resolve(message), message.getId(), ownerToken);
            metadata.setQosOwnerToken(null);
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常: packetId={}", packet.getPacketId(), e);
        }
    }
}
