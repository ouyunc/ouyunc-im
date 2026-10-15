package com.ouyunc.message.ratelimit;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 读取 {@code ouyunc.message.rate-limit}。首次成功读到服务端配置后固定下来，修改配置需要重启节点。
 * 单测或配置尚未初始化时使用与 {@code ouyunc-server.yml} 相同的默认值。
 * 过小的字节突发、推送突发和登录键过期会被抬高，避免合法请求永远通不过。
 */
public final class RateLimitSettings {

    private static final Logger log = LoggerFactory.getLogger(RateLimitSettings.class);

    private static final int DEFAULT_CONNECTION_PER_SECOND = 30;
    private static final int DEFAULT_CONNECTION_BURST = 120;
    private static final int DEFAULT_LOGIN_WINDOW_MILLIS = 60_000;
    private static final int DEFAULT_LOGIN_KEY_TTL_MILLIS = 120_000;
    private static final int DEFAULT_LOGIN_IDENTITY = 30;
    private static final int DEFAULT_LOGIN_IP = 1200;
    private static final int DEFAULT_SEND_MESSAGES_PER_SECOND = 30;
    private static final int DEFAULT_SEND_MESSAGE_BURST = 60;
    private static final int DEFAULT_SEND_BYTES_PER_SECOND = 1_048_576;
    private static final int DEFAULT_SEND_BYTE_BURST = 1_048_576;
    private static final int DEFAULT_SEND_CLOSE_THRESHOLD = 16;
    private static final int DEFAULT_PUSH_PER_SECOND = 200;
    private static final int DEFAULT_PUSH_BURST = 512;

    private static volatile Snapshot configured;

    private RateLimitSettings() {
    }

    public static boolean connectionEnabled() {
        return current().connectionEnabled;
    }

    public static double connectionPerIpPerSecond() {
        return current().connectionPerSecond;
    }

    public static double connectionPerIpBurst() {
        return current().connectionBurst;
    }

    public static boolean loginEnabled() {
        return current().loginEnabled;
    }

    public static long loginWindowMillis() {
        return current().loginWindowMillis;
    }

    public static long loginKeyTtlMillis() {
        return current().loginKeyTtlMillis;
    }

    public static long loginIdentityPerWindow() {
        return current().loginIdentityPerWindow;
    }

    public static long loginIpPerWindow() {
        return current().loginIpPerWindow;
    }

    public static boolean channelSendEnabled() {
        return current().channelSendEnabled;
    }

    public static double channelSendMessagesPerSecond() {
        return current().channelSendMessagesPerSecond;
    }

    public static double channelSendMessageBurst() {
        return current().channelSendMessageBurst;
    }

    public static double channelSendBytesPerSecond() {
        return current().channelSendBytesPerSecond;
    }

    public static double channelSendByteBurst() {
        return current().channelSendByteBurst;
    }

    public static int channelSendRejectCloseThreshold() {
        return current().channelSendRejectCloseThreshold;
    }

    public static boolean httpPushEnabled() {
        return current().httpPushEnabled;
    }

    public static double httpPushPerAppKeyPerSecond() {
        return current().httpPushPerSecond;
    }

    public static double httpPushPerAppKeyBurst() {
        return current().httpPushBurst;
    }

    /** 小于最大帧长时抬到最大帧长。 */
    static int clampChannelByteBurst(int configured) {
        int minimum = MessageConstant.MAX_FRAME_LENGTH;
        if (configured < minimum) {
            log.warn("channel-send.byte-burst={} 小于最大帧长 {}，已抬到最大帧长", configured, minimum);
            return minimum;
        }
        return configured;
    }

    /** 小于一次满员扇出时抬到 toList 上限。 */
    static int clampHttpPushBurst(int configured) {
        int minimum = MessageConstant.HTTP_PUSH_TO_LIST_MAX;
        if (configured < minimum) {
            log.warn("http-push.per-app-key-burst={} 小于 toList 上限 {}，已抬到该上限", configured, minimum);
            return minimum;
        }
        return configured;
    }

    /** 键过期短于窗口时抬到窗口的两倍，避免计数桶在窗口中途消失。 */
    static int clampLoginKeyTtl(int windowMillis, int configuredTtl) {
        if (configuredTtl >= windowMillis) {
            return configuredTtl;
        }
        int raised = (int) Math.min(Integer.MAX_VALUE, (long) windowMillis * 2L);
        log.warn("login.key-ttl-millis={} 短于窗口 {}，已抬到 {}", configuredTtl, windowMillis, raised);
        return raised;
    }

    private static Snapshot current() {
        Snapshot cached = configured;
        if (cached != null) {
            return cached;
        }
        MessageServerProperties properties = propertiesOrNull();
        if (properties == null) {
            return Snapshot.defaults();
        }
        synchronized (RateLimitSettings.class) {
            if (configured == null) {
                configured = Snapshot.from(properties);
            }
            return configured;
        }
    }

    private static MessageServerProperties propertiesOrNull() {
        try {
            return MessageServerContext.serverProperties();
        } catch (RuntimeException | LinkageError ex) {
            // 单测没有引导配置中心时，MessageServerContext 初始化会失败。这时用默认配额。
            return null;
        }
    }

    private static int positive(int configured, int fallback, String name) {
        if (configured > 0) {
            return configured;
        }
        log.warn("{}={} 无效，使用默认值 {}", name, configured, fallback);
        return fallback;
    }

    private static final class Snapshot {
        private final boolean connectionEnabled;
        private final double connectionPerSecond;
        private final double connectionBurst;
        private final boolean loginEnabled;
        private final long loginWindowMillis;
        private final long loginKeyTtlMillis;
        private final long loginIdentityPerWindow;
        private final long loginIpPerWindow;
        private final boolean channelSendEnabled;
        private final double channelSendMessagesPerSecond;
        private final double channelSendMessageBurst;
        private final double channelSendBytesPerSecond;
        private final double channelSendByteBurst;
        private final int channelSendRejectCloseThreshold;
        private final boolean httpPushEnabled;
        private final double httpPushPerSecond;
        private final double httpPushBurst;

        private Snapshot(boolean connectionEnabled, double connectionPerSecond, double connectionBurst,
                         boolean loginEnabled, long loginWindowMillis, long loginKeyTtlMillis,
                         long loginIdentityPerWindow, long loginIpPerWindow,
                         boolean channelSendEnabled, double channelSendMessagesPerSecond, double channelSendMessageBurst,
                         double channelSendBytesPerSecond, double channelSendByteBurst, int channelSendRejectCloseThreshold,
                         boolean httpPushEnabled, double httpPushPerSecond, double httpPushBurst) {
            this.connectionEnabled = connectionEnabled;
            this.connectionPerSecond = connectionPerSecond;
            this.connectionBurst = connectionBurst;
            this.loginEnabled = loginEnabled;
            this.loginWindowMillis = loginWindowMillis;
            this.loginKeyTtlMillis = loginKeyTtlMillis;
            this.loginIdentityPerWindow = loginIdentityPerWindow;
            this.loginIpPerWindow = loginIpPerWindow;
            this.channelSendEnabled = channelSendEnabled;
            this.channelSendMessagesPerSecond = channelSendMessagesPerSecond;
            this.channelSendMessageBurst = channelSendMessageBurst;
            this.channelSendBytesPerSecond = channelSendBytesPerSecond;
            this.channelSendByteBurst = channelSendByteBurst;
            this.channelSendRejectCloseThreshold = channelSendRejectCloseThreshold;
            this.httpPushEnabled = httpPushEnabled;
            this.httpPushPerSecond = httpPushPerSecond;
            this.httpPushBurst = httpPushBurst;
        }

        private static Snapshot defaults() {
            return new Snapshot(true, DEFAULT_CONNECTION_PER_SECOND, DEFAULT_CONNECTION_BURST,
                    true, DEFAULT_LOGIN_WINDOW_MILLIS, DEFAULT_LOGIN_KEY_TTL_MILLIS,
                    DEFAULT_LOGIN_IDENTITY, DEFAULT_LOGIN_IP,
                    true, DEFAULT_SEND_MESSAGES_PER_SECOND, DEFAULT_SEND_MESSAGE_BURST,
                    DEFAULT_SEND_BYTES_PER_SECOND, DEFAULT_SEND_BYTE_BURST, DEFAULT_SEND_CLOSE_THRESHOLD,
                    true, DEFAULT_PUSH_PER_SECOND, DEFAULT_PUSH_BURST);
        }

        private static Snapshot from(MessageServerProperties properties) {
            int window = positive(properties.getRateLimitLoginWindowMillis(), DEFAULT_LOGIN_WINDOW_MILLIS,
                    "login.window-millis");
            int closeThreshold = properties.getRateLimitChannelSendRejectCloseThreshold();
            if (closeThreshold < 1) {
                log.warn("channel-send.reject-close-threshold={} 无效，使用 1", closeThreshold);
                closeThreshold = 1;
            }
            return new Snapshot(
                    properties.isRateLimitConnectionEnabled(),
                    positive(properties.getRateLimitConnectionPerIpPerSecond(), DEFAULT_CONNECTION_PER_SECOND,
                            "connection.per-ip-per-second"),
                    positive(properties.getRateLimitConnectionPerIpBurst(), DEFAULT_CONNECTION_BURST,
                            "connection.per-ip-burst"),
                    properties.isRateLimitLoginEnabled(),
                    window,
                    clampLoginKeyTtl(window, positive(properties.getRateLimitLoginKeyTtlMillis(),
                            DEFAULT_LOGIN_KEY_TTL_MILLIS, "login.key-ttl-millis")),
                    positive(properties.getRateLimitLoginIdentityPerWindow(), DEFAULT_LOGIN_IDENTITY,
                            "login.identity-per-window"),
                    positive(properties.getRateLimitLoginIpPerWindow(), DEFAULT_LOGIN_IP, "login.ip-per-window"),
                    properties.isRateLimitChannelSendEnabled(),
                    positive(properties.getRateLimitChannelSendMessagesPerSecond(), DEFAULT_SEND_MESSAGES_PER_SECOND,
                            "channel-send.messages-per-second"),
                    positive(properties.getRateLimitChannelSendMessageBurst(), DEFAULT_SEND_MESSAGE_BURST,
                            "channel-send.message-burst"),
                    positive(properties.getRateLimitChannelSendBytesPerSecond(), DEFAULT_SEND_BYTES_PER_SECOND,
                            "channel-send.bytes-per-second"),
                    clampChannelByteBurst(positive(properties.getRateLimitChannelSendByteBurst(),
                            DEFAULT_SEND_BYTE_BURST, "channel-send.byte-burst")),
                    closeThreshold,
                    properties.isRateLimitHttpPushEnabled(),
                    positive(properties.getRateLimitHttpPushPerAppKeyPerSecond(), DEFAULT_PUSH_PER_SECOND,
                            "http-push.per-app-key-per-second"),
                    clampHttpPushBurst(positive(properties.getRateLimitHttpPushPerAppKeyBurst(),
                            DEFAULT_PUSH_BURST, "http-push.per-app-key-burst")));
        }
    }
}
