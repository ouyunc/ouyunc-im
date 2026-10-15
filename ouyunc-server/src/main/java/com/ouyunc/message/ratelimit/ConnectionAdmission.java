package com.ouyunc.message.ratelimit;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.ChannelAttrUtil;
import io.netty.channel.ChannelHandlerContext;

import java.time.Duration;

/**
 * 新建连接的本机准入。通过后把来源地址写到连接上，登录限流只读这个值。
 * 未通过或地址不可用时不写属性，调用方关闭连接。
 */
public final class ConnectionAdmission {

    private static volatile KeyedTokenAdmission admission;

    private ConnectionAdmission() {
    }

    /**
     * 按来源地址占用一个连接名额。地址只接受直连远端或可信 PROXY 源地址。
     * 限流关闭时仍写下来源地址，登录限流才能按同一地址计数。
     */
    public static boolean admit(ChannelHandlerContext ctx, String clientIp) {
        String normalized = normalize(clientIp);
        if (normalized == null) {
            return false;
        }
        if (RateLimitSettings.connectionEnabled() && !admission().allow(normalized, 1D)) {
            return false;
        }
        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_RATE_LIMIT_IP, normalized);
        return true;
    }

    private static KeyedTokenAdmission admission() {
        KeyedTokenAdmission current = admission;
        if (current != null) {
            return current;
        }
        synchronized (ConnectionAdmission.class) {
            if (admission == null) {
                admission = new KeyedTokenAdmission(
                        100_000L,
                        Duration.ofMinutes(2),
                        RateLimitSettings.connectionPerIpPerSecond(),
                        RateLimitSettings.connectionPerIpBurst());
            }
            return admission;
        }
    }

    /** 连接准入时记下的来源地址；没有则说明不能安全地按 IP 限流。 */
    public static String rateLimitIp(ChannelHandlerContext ctx) {
        return ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_RATE_LIMIT_IP);
    }

    /**
     * 过长或空地址直接拒绝，不做截断。截断会把不同地址并进同一个桶。
     */
    static String normalize(String clientIp) {
        if (clientIp == null) {
            return null;
        }
        String trimmed = clientIp.trim();
        if (trimmed.isEmpty() || trimmed.length() > 64 || "unknown".equalsIgnoreCase(trimmed)) {
            return null;
        }
        return trimmed;
    }
}
