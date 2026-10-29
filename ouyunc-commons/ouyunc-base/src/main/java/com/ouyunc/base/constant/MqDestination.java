package com.ouyunc.base.constant;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 测试/生产共用同一集群时，给逻辑名加环境前缀并转成大写。
 * 生产 prefix 为空，topic 仍是 {@code FRIEND_REQUEST}；测试为 {@code TEST_FRIEND_REQUEST}。
 * 消费组不在这里登记，订阅者把自己的组名传给 {@link #withPrefix(String)}。
 */
public final class MqDestination {

    private static final Logger log = LoggerFactory.getLogger(MqDestination.class);

    private static volatile String prefix = MessageConstant.EMPTY_STR;

    private MqDestination() {
    }

    /**
     * 空以及 {@link MqConstant#MQ_TOPIC_PREFIX_DISABLED_VALUES} 都不加前缀，避免改生产已有 topic。
     */
    public static void install(String raw) {
        String normalized = normalize(raw);
        prefix = normalized;
        log.info("MQ 环境前缀='{}', 示例 topic={}", normalized,
                topic(MqConstant.MQ_FRIEND_REQUEST_TOPIC));
    }

    public static String currentPrefix() {
        return prefix;
    }

    public static String topic(String logicalTopic) {
        return withPrefix(logicalTopic);
    }

    /**
     * 环境前缀 + 大写逻辑名。订阅者拼消费组时传入自己的组名，例如 {@code MESSAGE_TRANSLATE}。
     * 已带当前环境前缀时不再追加。
     */
    public static String withPrefix(String logicalName) {
        if (StringUtils.isBlank(logicalName)) {
            return logicalName;
        }
        String name = logicalName.trim().toUpperCase(Locale.ROOT);
        String head = prefixHead();
        if (head.isEmpty() || name.startsWith(head)) {
            return name;
        }
        return head + name;
    }

    public static String logical(String qualified) {
        if (StringUtils.isBlank(qualified)) {
            return qualified;
        }
        String name = qualified.trim().toUpperCase(Locale.ROOT);
        String head = prefixHead();
        if (head.isEmpty() || !name.startsWith(head)) {
            return name;
        }
        return name.substring(head.length());
    }

    private static String prefixHead() {
        String p = prefix;
        if (StringUtils.isBlank(p)) {
            return MessageConstant.EMPTY_STR;
        }
        return p.toUpperCase(Locale.ROOT) + MessageConstant.UNDERLINE;
    }

    private static String normalize(String raw) {
        if (StringUtils.isBlank(raw)) {
            return MessageConstant.EMPTY_STR;
        }
        String value = raw.trim().toLowerCase(Locale.ROOT);
        if (MqConstant.MQ_TOPIC_PREFIX_DISABLED_VALUES.contains(value)) {
            return MessageConstant.EMPTY_STR;
        }
        while (value.endsWith(MessageConstant.UNDERLINE) || value.endsWith(MessageConstant.HYPHEN)) {
            value = value.substring(NumberConstant.NUMBER_0, value.length() - NumberConstant.NUMBER_1);
        }
        return value.replace(MessageConstant.HYPHEN, MessageConstant.UNDERLINE);
    }
}
