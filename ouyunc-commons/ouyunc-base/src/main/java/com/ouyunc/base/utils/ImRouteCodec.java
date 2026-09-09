package com.ouyunc.base.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * 登录路由 HASH 的 field value：{@code nodeId|epoch}。nodeId 为 host:port，分隔符用 |。
 */
public final class ImRouteCodec {

    private static final char SEPARATOR = '|';

    private ImRouteCodec() {
    }

    public static String encode(String nodeId, long epoch) {
        return nodeId + SEPARATOR + epoch;
    }

    public static String nodeId(String encoded) {
        if (StringUtils.isBlank(encoded)) {
            return null;
        }
        int idx = encoded.lastIndexOf(SEPARATOR);
        if (idx <= 0) {
            return null;
        }
        return encoded.substring(0, idx);
    }

    public static long epoch(String encoded) {
        if (StringUtils.isBlank(encoded)) {
            return 0L;
        }
        int idx = encoded.lastIndexOf(SEPARATOR);
        if (idx < 0 || idx >= encoded.length() - 1) {
            return 0L;
        }
        try {
            return Long.parseLong(encoded.substring(idx + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }
}
