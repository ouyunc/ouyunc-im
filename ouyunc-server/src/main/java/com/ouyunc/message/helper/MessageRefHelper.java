package com.ouyunc.message.helper;

import com.ouyunc.base.constant.MessageConstant;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 消息引用 ref（packetId 列表）校验。
 */
public final class MessageRefHelper {

    private MessageRefHelper() {
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
