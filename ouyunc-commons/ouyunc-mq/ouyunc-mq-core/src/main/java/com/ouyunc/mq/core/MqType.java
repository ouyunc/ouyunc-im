package com.ouyunc.mq.core;

import org.apache.commons.lang3.StringUtils;

/**
 * 支持的 MQ 类型。
 */
public enum MqType {

    KAFKA,
    ROCKET;

    public static MqType from(String value) {
        if (StringUtils.isBlank(value)) {
            return KAFKA;
        }
        return switch (value.trim().toLowerCase()) {
            case "rocket", "rocketmq" -> ROCKET;
            default -> KAFKA;
        };
    }
}
