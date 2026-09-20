package com.ouyunc.message.schedule;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/** 原消息校验纯函数；通过仅表示消息关联有效，收件设备权限还须匹配始发节点任务。 */
public final class QosAckValidation {
    private QosAckValidation() {
    }

    public static boolean matches(Packet stored, String appKey, long packetId, String messageId) {
        if (stored == null || packetId <= 0 || stored.getPacketId() != packetId
                || StringUtils.isAnyBlank(appKey, messageId)) {
            return false;
        }
        Message message = stored.getMessage();
        return message != null && message.getQos() > 0
                && message.getMetadataOrNull() != null
                && appKey.equals(message.getMetadataOrNull().getAppKey())
                && messageId.equals(message.getId());
    }
}
