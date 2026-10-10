package com.ouyunc.config;

import com.ouyunc.base.config.ConfigConstant;
import com.ouyunc.base.config.ConfigTree;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

/**
 * 引导信息：外部文件位置，以及怎么连接配置中心。
 * 密码不进入 {@link #toString()}，避免启动日志把凭证打出去。
 */
public final class ConfigLocator {

    private final String externalFile;
    private final ConfigCenterType centerType;
    private final boolean failFast;
    private final String nacosServerAddr;
    private final String nacosNamespace;
    private final String nacosGroup;
    private final String nacosDataId;
    private final String nacosUsername;
    private final String nacosPassword;
    private final String nacosContextPath;
    private final String zkConnectString;
    private final String zkPath;

    private ConfigLocator(String externalFile, ConfigCenterType centerType, boolean failFast,
                          String nacosServerAddr, String nacosNamespace, String nacosGroup, String nacosDataId,
                          String nacosUsername, String nacosPassword, String nacosContextPath,
                          String zkConnectString, String zkPath) {
        this.externalFile = externalFile;
        this.centerType = centerType;
        this.failFast = failFast;
        this.nacosServerAddr = nacosServerAddr;
        this.nacosNamespace = nacosNamespace;
        this.nacosGroup = nacosGroup;
        this.nacosDataId = nacosDataId;
        this.nacosUsername = nacosUsername;
        this.nacosPassword = nacosPassword;
        this.nacosContextPath = nacosContextPath;
        this.zkConnectString = zkConnectString;
        this.zkPath = zkPath;
    }

    /**
     * 高优先级覆盖（参数、JVM、环境变量）已经折进 overrides。YAML 只补缺。
     */
    static ConfigLocator resolve(Map<String, Object> yaml, ConfigOverrides overrides) {
        ConfigCenterType centerType = ConfigCenterType.from(first(overrides, yaml,
                ConfigConstant.PROP_CENTER_TYPE, ConfigConstant.YAML_CENTER_TYPE, "none"));
        boolean failFast = parseBoolean(first(overrides, yaml,
                ConfigConstant.PROP_FAIL_FAST, ConfigConstant.YAML_FAIL_FAST, null), false);
        String externalFile = first(overrides, yaml, ConfigConstant.PROP_FILE, ConfigConstant.YAML_FILE, null);
        String nacosServerAddr = first(overrides, yaml, ConfigConstant.PROP_NACOS_ADDR, ConfigConstant.YAML_NACOS_ADDR, null);
        String nacosNamespace = first(overrides, yaml, ConfigConstant.PROP_NACOS_NAMESPACE, ConfigConstant.YAML_NACOS_NAMESPACE, null);
        String nacosGroup = defaultIfBlank(first(overrides, yaml,
                ConfigConstant.PROP_NACOS_GROUP, ConfigConstant.YAML_NACOS_GROUP, null), ConfigConstant.DEFAULT_NACOS_GROUP);
        String nacosDataId = defaultIfBlank(first(overrides, yaml,
                ConfigConstant.PROP_NACOS_DATA_ID, ConfigConstant.YAML_NACOS_DATA_ID, null), ConfigConstant.SERVER_FILE);
        String nacosUsername = first(overrides, yaml, ConfigConstant.PROP_NACOS_USERNAME, ConfigConstant.YAML_NACOS_USERNAME, null);
        String nacosPassword = first(overrides, yaml, ConfigConstant.PROP_NACOS_PASSWORD, ConfigConstant.YAML_NACOS_PASSWORD, null);
        String nacosContextPath = defaultIfBlank(first(overrides, yaml,
                ConfigConstant.PROP_NACOS_CONTEXT_PATH, ConfigConstant.YAML_NACOS_CONTEXT_PATH, null),
                ConfigConstant.DEFAULT_NACOS_CONTEXT_PATH);
        String zkConnectString = first(overrides, yaml, ConfigConstant.PROP_ZK_CONNECT, ConfigConstant.YAML_ZK_CONNECT, null);
        String zkPath = defaultIfBlank(first(overrides, yaml,
                ConfigConstant.PROP_ZK_PATH, ConfigConstant.YAML_ZK_PATH, null), ConfigConstant.DEFAULT_ZK_PATH);
        requireAddress(centerType, nacosServerAddr, zkConnectString);
        return new ConfigLocator(externalFile, centerType, failFast, nacosServerAddr, nacosNamespace, nacosGroup,
                nacosDataId, nacosUsername, nacosPassword, nacosContextPath, zkConnectString, zkPath);
    }

    public String externalFile() {
        return externalFile;
    }

    public ConfigCenterType centerType() {
        return centerType;
    }

    public boolean failFast() {
        return failFast;
    }

    public String nacosServerAddr() {
        return nacosServerAddr;
    }

    public String nacosNamespace() {
        return nacosNamespace;
    }

    public String nacosGroup() {
        return nacosGroup;
    }

    public String nacosDataId() {
        return nacosDataId;
    }

    public String nacosUsername() {
        return nacosUsername;
    }

    public String nacosPassword() {
        return nacosPassword;
    }

    public String nacosContextPath() {
        return nacosContextPath;
    }

    public String zkConnectString() {
        return zkConnectString;
    }

    public String zkPath() {
        return zkPath;
    }

    @Override
    public String toString() {
        return "ConfigLocator{centerType=" + centerType
                + ", failFast=" + failFast
                + ", externalFile=" + externalFile
                + ", nacosServerAddr=" + nacosServerAddr
                + ", nacosGroup=" + nacosGroup
                + ", nacosDataId=" + nacosDataId
                + ", zkPath=" + zkPath
                + "}";
    }

    private static void requireAddress(ConfigCenterType centerType, String nacosServerAddr, String zkConnectString) {
        if (centerType == ConfigCenterType.NACOS && StringUtils.isBlank(nacosServerAddr)) {
            throw new IllegalArgumentException("已启用 Nacos，但未配置 ouyunc.config.nacos.server-addr 或 OUYUNC_NACOS_ADDR");
        }
        if (centerType == ConfigCenterType.ZOOKEEPER && StringUtils.isBlank(zkConnectString)) {
            throw new IllegalArgumentException("已启用 ZooKeeper，但未配置 ouyunc.config.zk.connect-string 或 OUYUNC_ZK_CONNECT");
        }
    }

    private static String first(ConfigOverrides overrides, Map<String, Object> yaml,
                                String propKey, String yamlPath, String defaultValue) {
        String override = overrides.get(propKey);
        if (StringUtils.isNotBlank(override)) {
            return override.trim();
        }
        Object yamlValue = ConfigTree.find(yaml, yamlPath);
        if (yamlValue != null && StringUtils.isNotBlank(String.valueOf(yamlValue))) {
            return String.valueOf(yamlValue).trim();
        }
        return defaultValue;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return StringUtils.isBlank(value) ? defaultValue : value;
    }

    private static boolean parseBoolean(String raw, boolean defaultValue) {
        if (StringUtils.isBlank(raw)) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(raw) || "false".equalsIgnoreCase(raw)) {
            return Boolean.parseBoolean(raw);
        }
        throw new IllegalArgumentException("ouyunc.config.center.fail-fast 只能是 true 或 false: " + raw);
    }
}
