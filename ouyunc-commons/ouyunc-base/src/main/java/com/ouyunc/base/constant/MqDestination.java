package com.ouyunc.base.constant;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Locale;

/**
 * 测试/生产共用同一 Kafka 集群时，给 topic 和消费组加环境前缀。
 * 生产 prefix 为空，名称仍是 {@code ouyunc_friend_request}；测试为 {@code test_ouyunc_friend_request}。
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
        return qualify(logicalTopic);
    }

    public static String group(String logicalGroup) {
        return qualify(logicalGroup);
    }

    public static String logical(String qualified) {
        String p = prefix;
        if (StringUtils.isBlank(p) || StringUtils.isBlank(qualified)) {
            return qualified;
        }
        String head = prefixedHead(p);
        if (qualified.startsWith(head)) {
            return qualified.substring(head.length());
        }
        return qualified;
    }

    private static String qualify(String name) {
        if (StringUtils.isBlank(name)) {
            return name;
        }
        String p = prefix;
        if (StringUtils.isBlank(p)) {
            return name;
        }
        String head = prefixedHead(p);
        if (name.startsWith(head)) {
            return name;
        }
        return head + name;
    }

    private static String prefixedHead(String envPrefix) {
        return envPrefix + MessageConstant.UNDERLINE;
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
