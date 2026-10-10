package com.ouyunc.config;

/**
 * 配置中心类型。运行时只启用一个，由 {@code ouyunc.config.center.type} 决定。
 */
public enum ConfigCenterType {

    NONE,
    NACOS,
    ZOOKEEPER;

    public static ConfigCenterType from(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        return switch (raw.trim().toLowerCase()) {
            case "none", "local" -> NONE;
            case "nacos" -> NACOS;
            case "zk", "zookeeper" -> ZOOKEEPER;
            default -> throw new IllegalArgumentException("不支持的配置中心类型: " + raw);
        };
    }
}
