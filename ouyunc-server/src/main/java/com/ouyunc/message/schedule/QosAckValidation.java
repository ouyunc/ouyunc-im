package com.ouyunc.message.schedule;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * C2S ACK 与库中原消息的对应校验。
 * <p>客户端主要传 messageId；ackId（packetId）用于服务端定位下行重试任务。
 * 必须校验：该 packetId 对应消息的 messageId 与 ACK 声明一致，防止错取消。</p>
 */
public final class QosAckValidation {

    public enum MatchResult {
        OK,
        INVALID_ARGS,
        PACKET_MISSING,
        PACKET_ID_MISMATCH,
        /** ACK.messageId 与 packetId 指向消息的 messageId 不一致 */
        MESSAGE_ID_MISMATCH,
        APP_KEY_MISMATCH,
        QOS_DISABLED
    }

    private QosAckValidation() {
    }

    /**
     * @deprecated 使用 {@link #match(Packet, String, long, String)} 区分失败原因
     */
    @Deprecated
    public static boolean matches(Packet stored, String appKey, long packetId, String messageId) {
        return match(stored, appKey, packetId, messageId) == MatchResult.OK;
    }

    public static MatchResult match(Packet stored, String appKey, long packetId, String messageId) {
        if (packetId <= 0 || StringUtils.isAnyBlank(appKey, messageId)) {
            return MatchResult.INVALID_ARGS;
        }
        if (stored == null) {
            return MatchResult.PACKET_MISSING;
        }
        if (stored.getPacketId() != packetId) {
            return MatchResult.PACKET_ID_MISMATCH;
        }
        Message message = stored.getMessage();
        if (message == null || message.getQos() <= QosLevelEnum.QOS_0.getLevel()) {
            return MatchResult.QOS_DISABLED;
        }
        if (message.getMetadataOrNull() == null
                || !appKey.equals(message.getMetadataOrNull().getAppKey())) {
            return MatchResult.APP_KEY_MISMATCH;
        }
        if (!messageId.equals(message.getId())) {
            return MatchResult.MESSAGE_ID_MISMATCH;
        }
        return MatchResult.OK;
    }
}
