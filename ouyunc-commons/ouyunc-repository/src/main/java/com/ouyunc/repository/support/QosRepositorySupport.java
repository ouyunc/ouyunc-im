package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
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
        return QosIdempotencyHelper.isDuplicate(infra.redisTemplate, packet, channelLoginIdentity);
    }

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
        try {
            QosIdempotencyHelper.releaseClaim(infra.redisTemplate, metadata.getAppKey(),
                    packet.getPacketId(), message.getFrom(), message.getId());
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常: packetId={}", packet.getPacketId(), e);
        }
    }
}
