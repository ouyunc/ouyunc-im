package com.ouyunc.message.http;

/**
 * 路径模板判断（含 {@code {var}} 占位符）。
 */
public final class HttpPathTemplate {

    private HttpPathTemplate() {
    }

    public static boolean isTemplate(String normalizedPath) {
        return normalizedPath != null && normalizedPath.contains("{") && normalizedPath.contains("}");
    }
}
