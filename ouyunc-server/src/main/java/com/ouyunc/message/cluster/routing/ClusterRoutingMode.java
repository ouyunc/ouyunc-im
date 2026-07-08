package com.ouyunc.message.cluster.routing;

import org.apache.commons.lang3.StringUtils;

/**
 * 集群消息路由模式。
 */
public enum ClusterRoutingMode {

    /** 全集群任意节点中转（默认，兼容旧行为） */
    FLAT,

    /** 分区内自由路由，跨分区经网关中转 */
    ZONE_AWARE;

    public static ClusterRoutingMode from(String value) {
        if (StringUtils.isBlank(value)) {
            return FLAT;
        }
        return switch (value.trim().toLowerCase()) {
            case "zone-aware", "zone_aware", "zoneaware" -> ZONE_AWARE;
            default -> FLAT;
        };
    }
}
