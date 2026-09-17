package com.ouyunc.base.utils;

import org.apache.commons.lang3.StringUtils;

/**
 * 登录路由 HASH 的 field value：{@code nodeId|epoch|lastLoginTime}。
 * nodeId 为 host:port 可含冒号，分隔符用 |；末段为绑定时间，供 Lua fencing。
 * 两段旧值 {@code nodeId|epoch} 仍可解析（lastLoginTime=0）。
 */
public final class ImRouteCodec {

    private static final char SEPARATOR = '|';

    private ImRouteCodec() {
    }

    public static String encode(String nodeId, long epoch) {
        return encode(nodeId, epoch, 0L);
    }

    public static String encode(String nodeId, long epoch, long lastLoginTime) {
        return nodeId + SEPARATOR + epoch + SEPARATOR + lastLoginTime;
    }

    public static String nodeId(String encoded) {
        int last = lastSep(encoded);
        if (last <= 0) {
            return null;
        }
        int mid = encoded.lastIndexOf(SEPARATOR, last - 1);
        if (mid <= 0) {
            return encoded.substring(0, last);
        }
        return encoded.substring(0, mid);
    }

    public static long epoch(String encoded) {
        int last = lastSep(encoded);
        if (last < 0 || last >= encoded.length() - 1) {
            return 0L;
        }
        int mid = encoded.lastIndexOf(SEPARATOR, last - 1);
        try {
            if (mid < 0) {
                return Long.parseLong(encoded.substring(last + 1));
            }
            return Long.parseLong(encoded.substring(mid + 1, last));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    public static long lastLoginTime(String encoded) {
        int last = lastSep(encoded);
        if (last < 0 || last >= encoded.length() - 1) {
            return 0L;
        }
        int mid = encoded.lastIndexOf(SEPARATOR, last - 1);
        if (mid < 0) {
            return 0L;
        }
        try {
            return Long.parseLong(encoded.substring(last + 1));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    private static int lastSep(String encoded) {
        if (StringUtils.isBlank(encoded)) {
            return -1;
        }
        return encoded.lastIndexOf(SEPARATOR);
    }
}
