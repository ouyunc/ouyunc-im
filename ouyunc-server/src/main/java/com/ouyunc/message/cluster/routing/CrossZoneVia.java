package com.ouyunc.message.cluster.routing;

import org.apache.commons.lang3.StringUtils;

/**
 * 跨分区路由策略。
 */
public enum CrossZoneVia {

    /** 跨分区仅经各分区 gateway 节点 */
    GATEWAY,

    /** 跨分区允许任意可达节点（兼容 flat 行为） */
    ANY;

    public static CrossZoneVia from(String value) {
        if (StringUtils.isBlank(value)) {
            return GATEWAY;
        }
        return switch (value.trim().toLowerCase()) {
            case "any" -> ANY;
            default -> GATEWAY;
        };
    }
}
