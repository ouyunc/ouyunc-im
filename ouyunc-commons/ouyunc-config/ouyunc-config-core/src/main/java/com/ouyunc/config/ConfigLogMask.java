package com.ouyunc.config;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 把配置原文打到日志前，掩盖密码、密钥和令牌。
 */
public final class ConfigLogMask {

    private static final Pattern SENSITIVE_LINE = Pattern.compile(
            "(?i)(^\\s*[\\w.-]*(?:password|secret|token|passwd|saslJaasConfig|sasl\\.jaas\\.config)\\s*:\\s*)(.+)$");

    private ConfigLogMask() {
    }

    public static String maskSensitive(String yaml) {
        if (yaml == null || yaml.isEmpty()) {
            return yaml;
        }
        String[] lines = yaml.split("\\R", -1);
        StringBuilder masked = new StringBuilder(yaml.length());
        for (int i = 0; i < lines.length; i++) {
            if (i > 0) {
                masked.append('\n');
            }
            Matcher matcher = SENSITIVE_LINE.matcher(lines[i]);
            if (matcher.matches() && !matcher.group(2).isBlank()) {
                masked.append(matcher.group(1)).append("******");
            } else {
                masked.append(lines[i]);
            }
        }
        return masked.toString();
    }
}
