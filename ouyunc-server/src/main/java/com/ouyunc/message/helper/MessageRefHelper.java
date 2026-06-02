package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 消息引用 ref（packetId 列表）校验。
 */
public final class MessageRefHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageRefHelper.class);

    private MessageRefHelper() {
    }

    /** 校验 message.ref（packetId 列表，最多 {@link MessageConstant#MAX_REF_COUNT} 条） */
    public static boolean normalizeMessageRefOrReject(Packet packet) {
        Message message = packet.getMessage();
        if (message == null || CollectionUtils.isEmpty(message.getRef())) {
            return true;
        }
        try {
            message.setRef(normalizeAndValidate(message.getRef()));
            return true;
        } catch (IllegalArgumentException ex) {
            log.warn("消息引用校验失败: {} | packet={}", ex.getMessage(), packet);
            MessageServerContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(ExceptionCodeEnum.MESSAGE_REF_INVALID_ERROR, ex.getMessage(), packet),
                    MessageEventTypeEnum.EXCEPTION), true);
            return false;
        }
    }

    /**
     * 去重、去空；超过 {@link MessageConstant#MAX_REF_COUNT} 时抛异常。
     */
    public static List<String> normalizeAndValidate(List<String> rawRef) {
        if (CollectionUtils.isEmpty(rawRef)) {
            return null;
        }
        LinkedHashSet<String> normalized = new LinkedHashSet<>();
        for (String item : rawRef) {
            if (StringUtils.isBlank(item)) {
                continue;
            }
            String trimmed = StringUtils.trim(item);
            if (!StringUtils.isNumeric(trimmed) || "0".equals(trimmed)) {
                throw new IllegalArgumentException("引用消息 id 无效: " + trimmed);
            }
            normalized.add(trimmed);
        }
        if (normalized.isEmpty()) {
            return null;
        }
        if (normalized.size() > MessageConstant.MAX_REF_COUNT) {
            throw new IllegalArgumentException("引用消息超过上限 " + MessageConstant.MAX_REF_COUNT);
        }
        return new ArrayList<>(normalized);
    }
}
