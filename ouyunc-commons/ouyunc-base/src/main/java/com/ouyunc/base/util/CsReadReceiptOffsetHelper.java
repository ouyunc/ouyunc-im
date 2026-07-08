package com.ouyunc.base.util;

import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;

/**
 * 客服已读回执 offset 落库字段解析（与 Redis ticket sro 阅读者 id 对齐）。
 */
public final class CsReadReceiptOffsetHelper {

    private CsReadReceiptOffsetHelper() {
    }

    /**
     * 阅读者真实 id：优先 {@code metadata.csReaderId}（IM 写入），否则访客取 {@code from}。
     */
    public static String resolveReaderId(Message message) {
        if (message == null) {
            return null;
        }
        if (message.getMetadata() != null && StringUtils.isNotBlank(message.getMetadata().getCsReaderId())) {
            return message.getMetadata().getCsReaderId().trim();
        }
        if (message.getFromType() == MessageFromToTypeEnum.CS_VISITOR.getType()) {
            return StringUtils.isNotBlank(message.getFrom()) ? message.getFrom().trim() : null;
        }
        return null;
    }

    public static String resolveTicketId(Message message) {
        if (message == null || StringUtils.isBlank(message.getCorrelationId())) {
            return null;
        }
        return message.getCorrelationId().trim();
    }
}
