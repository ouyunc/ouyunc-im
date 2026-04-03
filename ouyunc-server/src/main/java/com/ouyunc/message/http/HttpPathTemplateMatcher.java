package com.ouyunc.message.http;

import java.util.Map;

/**
 * 将路径模板（按 {@code /} 分段，段内可为字面量或 {@code {name}}）与真实路径匹配并提取变量。
 */
public final class HttpPathTemplateMatcher {

    private HttpPathTemplateMatcher() {
    }

    /**
     * 将已规范化路径按 {@code /} 分段（与模板、真实路径规则一致）。
     */
    public static String[] segments(String normalizedPath) {
        return splitSegments(normalizedPath);
    }

    public static boolean isVariableSegment(String segment) {
        return segment != null
                && segment.length() >= 3
                && segment.charAt(0) == '{'
                && segment.charAt(segment.length() - 1) == '}';
    }

    public static String variableName(String variableSegment) {
        return variableSegment.substring(1, variableSegment.length() - 1).trim();
    }

    /**
     * @param patternPath 已规范化路径，如 {@code /api/user/{id}/item}
     * @param actualPath    请求路径，已规范化
     * @param out           输出路径变量，匹配成功时写入
     * @return 是否匹配
     */
    public static boolean match(String patternPath, String actualPath, Map<String, String> out) {
        if (patternPath == null || actualPath == null) {
            return false;
        }
        String[] ps = splitSegments(patternPath);
        String[] as = splitSegments(actualPath);
        if (ps.length != as.length) {
            return false;
        }
        for (int i = 0; i < ps.length; i++) {
            String p = ps[i];
            if (isVariableSegment(p)) {
                String name = variableName(p);
                if (name.isEmpty()) {
                    return false;
                }
                out.put(name, as[i]);
            } else if (!p.equals(as[i])) {
                return false;
            }
        }
        return true;
    }

    private static String[] splitSegments(String path) {
        if (path == null || path.isEmpty() || "/".equals(path)) {
            return new String[0];
        }
        String p = path;
        if (p.startsWith("/")) {
            p = p.substring(1);
        }
        if (p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        if (p.isEmpty()) {
            return new String[0];
        }
        return p.split("/");
    }
}
