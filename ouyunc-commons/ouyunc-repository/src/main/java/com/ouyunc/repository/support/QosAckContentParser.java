package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.lang3.StringUtils;

import java.util.regex.Pattern;

/**
 * 解析 QoS ACK 载荷（兼容 JSON 与纯 packetId 字符串）
 */
public final class QosAckContentParser {

    private static final Pattern NUMERIC_ID_PATTERN = Pattern.compile("\\d{1,20}");

    private QosAckContentParser() {
    }

    public static String resolveAckId(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (trimmed.startsWith("{")) {
            QosAckContent parsed = JSON.parseObject(trimmed, QosAckContent.class);
            return parsed != null ? parsed.getAckId() : null;
        }
        return trimmed;
    }

    public static String resolveClientMessageId(String content) {
        if (StringUtils.isBlank(content)) {
            return null;
        }
        String trimmed = content.trim();
        if (!trimmed.startsWith("{")) {
            return null;
        }
        QosAckContent parsed = JSON.parseObject(trimmed, QosAckContent.class);
        return parsed != null ? parsed.getMessageId() : null;
    }

    /**
     * 解析对外 ackId 为内部 long packetId（十进制串优先；兼容历史 CosId 19 位串）
     */
    public static long resolveAckPacketId(String content) {
        String ackId = resolveAckId(content);
        if (StringUtils.isBlank(ackId)) {
            return 0L;
        }
        String trimmed = ackId.trim();
        if (NUMERIC_ID_PATTERN.matcher(trimmed).matches()) {
            return Long.parseLong(trimmed);
        }
        return MessageContext.idGenerator().formatStrIdAsLong(trimmed);
    }
}
