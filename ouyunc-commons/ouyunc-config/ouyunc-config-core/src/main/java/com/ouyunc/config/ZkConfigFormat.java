package com.ouyunc.config;

/**
 * ZooKeeper 配置节点格式。
 * <ul>
 *   <li>{@link #YAML}：节点 data 是整份 YAML 文档（默认，兼容现网）</li>
 *   <li>{@link #PROPERTIES}：父路径下子节点名=key、data=value（BMS 风格）</li>
 * </ul>
 */
public enum ZkConfigFormat {

    YAML,
    PROPERTIES;

    public static ZkConfigFormat from(String raw) {
        if (raw == null || raw.isBlank()) {
            return YAML;
        }
        return switch (raw.trim().toLowerCase()) {
            case "yaml", "yml" -> YAML;
            case "properties", "props", "kv" -> PROPERTIES;
            default -> throw new IllegalArgumentException(
                    "不支持的 ZooKeeper 配置格式: " + raw + "，仅支持 yaml 或 properties");
        };
    }
}
