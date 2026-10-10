package com.ouyunc.config;

import com.ouyunc.base.config.ConfigConstant;
import org.apache.commons.lang3.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 把启动参数、JVM 参数、环境变量折成同一组引导键。
 * 优先级：{@code --key=value} &gt; {@code -Dkey=value} &gt; 环境变量。
 */
final class ConfigOverrides {

    private final Map<String, String> values = new LinkedHashMap<>();

    private ConfigOverrides() {
    }

    static ConfigOverrides capture(String[] args) {
        ConfigOverrides overrides = new ConfigOverrides();
        overrides.readArgs(args);
        overrides.readSystemProperties();
        overrides.readEnvironment();
        return overrides;
    }

    String get(String propKey) {
        return values.get(propKey);
    }

    private void readArgs(String[] args) {
        if (args == null) {
            return;
        }
        for (String arg : args) {
            if (arg == null || !arg.startsWith("--")) {
                continue;
            }
            String text = arg.substring(2);
            int equals = text.indexOf('=');
            if (equals <= 0) {
                continue;
            }
            String key = text.substring(0, equals);
            if (key.startsWith("ouyunc.config.")) {
                putIfAbsent(key, text.substring(equals + 1));
            }
        }
    }

    private void readSystemProperties() {
        putIfAbsent(ConfigConstant.PROP_FILE, System.getProperty(ConfigConstant.PROP_FILE));
        putIfAbsent(ConfigConstant.PROP_CENTER_TYPE, System.getProperty(ConfigConstant.PROP_CENTER_TYPE));
        putIfAbsent(ConfigConstant.PROP_FAIL_FAST, System.getProperty(ConfigConstant.PROP_FAIL_FAST));
        putIfAbsent(ConfigConstant.PROP_NACOS_ADDR, System.getProperty(ConfigConstant.PROP_NACOS_ADDR));
        putIfAbsent(ConfigConstant.PROP_NACOS_NAMESPACE, System.getProperty(ConfigConstant.PROP_NACOS_NAMESPACE));
        putIfAbsent(ConfigConstant.PROP_NACOS_GROUP, System.getProperty(ConfigConstant.PROP_NACOS_GROUP));
        putIfAbsent(ConfigConstant.PROP_NACOS_DATA_ID, System.getProperty(ConfigConstant.PROP_NACOS_DATA_ID));
        putIfAbsent(ConfigConstant.PROP_NACOS_USERNAME, System.getProperty(ConfigConstant.PROP_NACOS_USERNAME));
        putIfAbsent(ConfigConstant.PROP_NACOS_PASSWORD, System.getProperty(ConfigConstant.PROP_NACOS_PASSWORD));
        putIfAbsent(ConfigConstant.PROP_NACOS_CONTEXT_PATH, System.getProperty(ConfigConstant.PROP_NACOS_CONTEXT_PATH));
        putIfAbsent(ConfigConstant.PROP_ZK_CONNECT, System.getProperty(ConfigConstant.PROP_ZK_CONNECT));
        putIfAbsent(ConfigConstant.PROP_ZK_PATH, System.getProperty(ConfigConstant.PROP_ZK_PATH));
    }

    private void readEnvironment() {
        putIfAbsent(ConfigConstant.PROP_FILE, System.getenv(ConfigConstant.ENV_FILE));
        putIfAbsent(ConfigConstant.PROP_CENTER_TYPE, System.getenv(ConfigConstant.ENV_CENTER_TYPE));
        putIfAbsent(ConfigConstant.PROP_FAIL_FAST, System.getenv(ConfigConstant.ENV_FAIL_FAST));
        putIfAbsent(ConfigConstant.PROP_NACOS_ADDR, System.getenv(ConfigConstant.ENV_NACOS_ADDR));
        putIfAbsent(ConfigConstant.PROP_NACOS_NAMESPACE, System.getenv(ConfigConstant.ENV_NACOS_NAMESPACE));
        putIfAbsent(ConfigConstant.PROP_NACOS_GROUP, System.getenv(ConfigConstant.ENV_NACOS_GROUP));
        putIfAbsent(ConfigConstant.PROP_NACOS_DATA_ID, System.getenv(ConfigConstant.ENV_NACOS_DATA_ID));
        putIfAbsent(ConfigConstant.PROP_NACOS_USERNAME, System.getenv(ConfigConstant.ENV_NACOS_USERNAME));
        putIfAbsent(ConfigConstant.PROP_NACOS_PASSWORD, System.getenv(ConfigConstant.ENV_NACOS_PASSWORD));
        putIfAbsent(ConfigConstant.PROP_NACOS_CONTEXT_PATH, System.getenv(ConfigConstant.ENV_NACOS_CONTEXT_PATH));
        putIfAbsent(ConfigConstant.PROP_ZK_CONNECT, System.getenv(ConfigConstant.ENV_ZK_CONNECT));
        putIfAbsent(ConfigConstant.PROP_ZK_PATH, System.getenv(ConfigConstant.ENV_ZK_PATH));
    }

    private void putIfAbsent(String key, String value) {
        if (StringUtils.isBlank(value) || values.containsKey(key)) {
            return;
        }
        values.put(key, value.trim());
    }
}
