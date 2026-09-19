package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.packet.message.content.QosAckContent;
import org.apache.commons.lang3.StringUtils;

/**
 * 解析 QoS ACK 载荷，仅接受 JSON：{@code {"ackId":"<packetId>","messageId":"<客户端消息id>"}}。
 */
public final class QosAckContentParser {

    private QosAckContentParser() {
    }

    public static QosAckContent parse(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        QosAckContent parsed = JSON.parseObject(trimmed, QosAckContent.class);
        if (parsed == null || StringUtils.isBlank(parsed.getAckId())) {
            return null;
        }
        return parsed;
    }

    /**
     * ackId 为服务端 packetId 十进制字符串。
     */
    public static long toPacketId(String ackId) {
        if (StringUtils.isBlank(ackId)) {
            return 0L;
        }
        try {
            return Long.parseLong(ackId.trim());
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
