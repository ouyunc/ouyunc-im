package com.ouyunc.base.executor;

import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.time.Duration;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * 线程池的声明式配置。
 * 此配置可从 YAML（参见 {@code ouyunc-server.yml}）、系统属性或环境变量中加载
 */
public final class ThreadPoolConfig {

    private static final String CONFIG_PREFIX = "ouyunc.message.thread-pool.";

    private final EnumMap<ThreadPoolId, PoolConfig> configs;

    private ThreadPoolConfig(EnumMap<ThreadPoolId, PoolConfig> configs) {
        this.configs = configs;
    }

    public PoolConfig get(ThreadPoolId id) {
        return configs.get(id);
    }

    public EnumMap<ThreadPoolId, PoolConfig> getAll() {
        return new EnumMap<>(configs);
    }

    public static ThreadPoolConfig defaultConfig() {
        EnumMap<ThreadPoolId, PoolConfig> defaults = new EnumMap<>(ThreadPoolId.class);
        defaults.put(ThreadPoolId.MESSAGE_SEND, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("message-send")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.MESSAGE_PROCESSOR, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("message-processor")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.QOS_TASK, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("qos-task")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.ROUTER, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("router")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.REPOSITORY, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("repository")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.EVENT_LISTENER, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("event-listener")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.CLUSTER_CLIENT_HEARTBEAT, PoolConfig.builder()
                .type(ThreadPoolType.SCHEDULED)
                .coreSize(1)
                .threadNamePrefix("cluster-client-heartbeat")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.SYSTEM_CLOCK, PoolConfig.builder()
                .type(ThreadPoolType.SCHEDULED)
                .coreSize(1)
                .threadNamePrefix("system-clock")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.REDIS_PUBSUB, PoolConfig.builder()
                .type(ThreadPoolType.SINGLE)
                .threadNamePrefix("redis-pubsub")
                .daemon(true)
                .build());
        defaults.put(ThreadPoolId.HTTP_PUSH_VERIFY, PoolConfig.builder()
                .type(ThreadPoolType.VIRTUAL)
                .threadNamePrefix("http-push-verify")
                .daemon(true)
                .build());
        return new ThreadPoolConfig(defaults);
    }

    /**
     * 根据 YAML 映射以及覆盖配置构建配置。
     * 参数说明：
     * @param yamlSection 从 YAML 中提取的配置片段（可为 null）
     * @param overrides 覆盖配置映射（例如系统属性）
     */
    @SuppressWarnings("unchecked")
    public static ThreadPoolConfig from(Map<String, ?> yamlSection, Map<String, String> overrides) {
        ThreadPoolConfig base = defaultConfig();
        EnumMap<ThreadPoolId, PoolConfig> merged = base.getAll();
        if (MapUtils.isNotEmpty(yamlSection)) {
            for (Map.Entry<String, ?> entry : yamlSection.entrySet()) {
                ThreadPoolId id = ThreadPoolId.fromConfigKey(entry.getKey());
                if (id == null) {
                    continue;
                }
                Object node = entry.getValue();
                if (node instanceof Map<?, ?> nodeMap) {
                    merged.put(id, merge(merged.get(id), (Map<String, Object>) nodeMap));
                }
            }
        }
        if (MapUtils.isNotEmpty(overrides)) {
            overrides.forEach((key, value) -> {
                if (!key.startsWith(CONFIG_PREFIX)) {
                    return;
                }
                String remainder = key.substring(CONFIG_PREFIX.length());
                int dotIndex = remainder.indexOf('.');
                if (dotIndex <= 0) {
                    return;
                }
                String poolKey = remainder.substring(0, dotIndex);
                String property = remainder.substring(dotIndex + 1);
                ThreadPoolId id = ThreadPoolId.fromConfigKey(poolKey);
                if (id == null) {
                    return;
                }
                PoolConfig current = merged.getOrDefault(id, PoolConfig.builder().build());
                merged.put(id, applyOverride(current, property, value));
            });
        }
        return new ThreadPoolConfig(merged);
    }

    private static PoolConfig merge(PoolConfig base, Map<String, Object> overrides) {
        PoolConfig.Builder builder = PoolConfig.builder(base);
        // 先处理 type，确定线程池类型
        ThreadPoolType[] poolTypeRef = {base.type()};
        Object typeValue = overrides.get("type");
        if (typeValue != null) {
            poolTypeRef[0] = parseType(typeValue, base.type());
            builder.type(poolTypeRef[0]);
        }
        final ThreadPoolType poolType = poolTypeRef[0];
        
        overrides.forEach((key, value) -> {
            switch (key.toLowerCase(Locale.ROOT)) {
                case "type" -> {
                    // 已在上面处理
                }
                case "core-size" -> {
                    // 仅对 SCHEDULED 和 FIXED 有效
                    if (poolType == ThreadPoolType.SCHEDULED || poolType == ThreadPoolType.FIXED) {
                        builder.coreSize(asInt(value, base.coreSize()));
                    }
                }
                case "max-size" -> {
                    // 仅对 FIXED 和 CACHED 有效
                    if (poolType == ThreadPoolType.FIXED || poolType == ThreadPoolType.CACHED) {
                        builder.maxSize(asInt(value, base.maxSize()));
                    }
                }
                case "queue-capacity" -> {
                    // 仅对 FIXED 有效
                    if (poolType == ThreadPoolType.FIXED) {
                        builder.queueCapacity(asInt(value, base.queueCapacity()));
                    }
                }
                case "keep-alive-seconds" -> {
                    // 仅对 FIXED 和 CACHED 有效
                    if (poolType == ThreadPoolType.FIXED || poolType == ThreadPoolType.CACHED) {
                        builder.keepAliveSeconds(asLong(value, base.keepAliveSeconds()));
                    }
                }
                case "thread-name-prefix" -> builder.threadNamePrefix(asString(value, base.threadNamePrefix()));
                case "daemon" -> builder.daemon(asBoolean(value, base.daemon()));
                case "allow-core-timeout" -> {
                    // 仅对 FIXED 和 SCHEDULED 有效
                    if (poolType == ThreadPoolType.FIXED || poolType == ThreadPoolType.SCHEDULED) {
                        builder.allowCoreThreadTimeout(asBoolean(value, base.allowCoreThreadTimeout()));
                    }
                }
                default -> {
                }
            }
        });
        return builder.build();
    }

    private static PoolConfig applyOverride(PoolConfig base, String property, String rawValue) {
        PoolConfig.Builder builder = PoolConfig.builder(base);
        String normalized = property.toLowerCase(Locale.ROOT);
        ThreadPoolType poolType = base.type();
        
        // 如果覆盖的是 type，先更新类型
        if ("type".equals(normalized)) {
            poolType = parseType(rawValue, base.type());
            builder.type(poolType);
        }
        
        final ThreadPoolType finalPoolType = poolType;
        switch (normalized) {
            case "type" -> {
                // 已在上面处理
            }
            case "core-size" -> {
                if (finalPoolType == ThreadPoolType.SCHEDULED || finalPoolType == ThreadPoolType.FIXED) {
                    builder.coreSize(Integer.parseInt(rawValue));
                }
            }
            case "max-size" -> {
                if (finalPoolType == ThreadPoolType.FIXED || finalPoolType == ThreadPoolType.CACHED) {
                    builder.maxSize(Integer.parseInt(rawValue));
                }
            }
            case "queue-capacity" -> {
                if (finalPoolType == ThreadPoolType.FIXED) {
                    builder.queueCapacity(Integer.parseInt(rawValue));
                }
            }
            case "keep-alive-seconds" -> {
                if (finalPoolType == ThreadPoolType.FIXED || finalPoolType == ThreadPoolType.CACHED) {
                    builder.keepAliveSeconds(Long.parseLong(rawValue));
                }
            }
            case "thread-name-prefix" -> builder.threadNamePrefix(rawValue);
            case "daemon" -> builder.daemon(Boolean.parseBoolean(rawValue));
            case "allow-core-timeout" -> {
                if (finalPoolType == ThreadPoolType.FIXED || finalPoolType == ThreadPoolType.SCHEDULED) {
                    builder.allowCoreThreadTimeout(Boolean.parseBoolean(rawValue));
                }
            }
            default -> {
            }
        }
        return builder.build();
    }

    private static ThreadPoolType parseType(Object value, ThreadPoolType defaultType) {
        if (value == null) {
            return defaultType;
        }
        String raw = value.toString().trim();
        if (StringUtils.isNotBlank(raw)) {
            try {
                return ThreadPoolType.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignore) {
                return defaultType;
            }
        }
        return defaultType;
    }

    private static int asInt(Object value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        String raw = value.toString().trim();
        return raw.isEmpty() ? defaultValue : Integer.parseInt(raw);
    }

    private static long asLong(Object value, long defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return defaultValue;
        }
        if (raw.endsWith("s") || raw.endsWith("S")) {
            return Duration.parse(raw.toUpperCase(Locale.ROOT)).getSeconds();
        }
        return Long.parseLong(raw);
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        String raw = value.toString().trim();
        return raw.isEmpty() ? defaultValue : Boolean.parseBoolean(raw);
    }

    private static String asString(Object value, String defaultValue) {
        return value == null ? defaultValue : value.toString();
    }

    /**
     * 管理线程池配置
     */
    public static final class PoolConfig {
        private final ThreadPoolType type;
        private final int coreSize;
        private final int maxSize;
        private final int queueCapacity;
        private final long keepAliveSeconds;
        private final String threadNamePrefix;
        private final boolean daemon;
        private final boolean allowCoreThreadTimeout;

        private PoolConfig(Builder builder) {
            this.type = builder.type;
            this.coreSize = builder.coreSize;
            this.maxSize = builder.maxSize;
            this.queueCapacity = builder.queueCapacity;
            this.keepAliveSeconds = builder.keepAliveSeconds;
            this.threadNamePrefix = builder.threadNamePrefix;
            this.daemon = builder.daemon;
            this.allowCoreThreadTimeout = builder.allowCoreThreadTimeout;
        }

        public ThreadPoolType type() {
            return type;
        }

        public int coreSize() {
            return coreSize;
        }

        public int maxSize() {
            return maxSize;
        }

        public int queueCapacity() {
            return queueCapacity;
        }

        public long keepAliveSeconds() {
            return keepAliveSeconds;
        }

        public String threadNamePrefix() {
            return threadNamePrefix;
        }

        public boolean daemon() {
            return daemon;
        }

        public boolean allowCoreThreadTimeout() {
            return allowCoreThreadTimeout;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof PoolConfig that)) return false;
            return coreSize == that.coreSize
                    && maxSize == that.maxSize
                    && queueCapacity == that.queueCapacity
                    && keepAliveSeconds == that.keepAliveSeconds
                    && daemon == that.daemon
                    && allowCoreThreadTimeout == that.allowCoreThreadTimeout
                    && type == that.type
                    && Objects.equals(threadNamePrefix, that.threadNamePrefix);
        }

        @Override
        public int hashCode() {
            return Objects.hash(type, coreSize, maxSize, queueCapacity, keepAliveSeconds, threadNamePrefix, daemon, allowCoreThreadTimeout);
        }

        @Override
        public String toString() {
            return "PoolConfig{" +
                    "type=" + type +
                    ", coreSize=" + coreSize +
                    ", maxSize=" + maxSize +
                    ", queueCapacity=" + queueCapacity +
                    ", keepAliveSeconds=" + keepAliveSeconds +
                    ", threadNamePrefix='" + threadNamePrefix + '\'' +
                    ", daemon=" + daemon +
                    ", allowCoreThreadTimeout=" + allowCoreThreadTimeout +
                    '}';
        }

        public static Builder builder() {
            return new Builder();
        }

        public static Builder builder(PoolConfig base) {
            return new Builder(base);
        }

        public static final class Builder {
            private ThreadPoolType type = ThreadPoolType.VIRTUAL;
            private int coreSize = Runtime.getRuntime().availableProcessors();
            private int maxSize = coreSize;
            private int queueCapacity = -1;  // -1 表示无界队列
            private long keepAliveSeconds = 60;
            private String threadNamePrefix = "thread-pool";
            private boolean daemon = true;
            private boolean allowCoreThreadTimeout = false;  // 默认 false，保持核心线程常驻

            private Builder() {
            }

            private Builder(PoolConfig base) {
                if (base != null) {
                    this.type = base.type;
                    this.coreSize = base.coreSize;
                    this.maxSize = base.maxSize;
                    this.queueCapacity = base.queueCapacity;
                    this.keepAliveSeconds = base.keepAliveSeconds;
                    this.threadNamePrefix = base.threadNamePrefix;
                    this.daemon = base.daemon;
                    this.allowCoreThreadTimeout = base.allowCoreThreadTimeout;
                }
            }

            public Builder type(ThreadPoolType type) {
                if (type != null) {
                    this.type = type;
                }
                return this;
            }

            public Builder coreSize(int coreSize) {
                if (coreSize > 0) {
                    this.coreSize = coreSize;
                }
                return this;
            }

            public Builder maxSize(int maxSize) {
                if (maxSize > 0) {
                    this.maxSize = maxSize;
                }
                return this;
            }

            public Builder queueCapacity(int queueCapacity) {
                this.queueCapacity = queueCapacity;
                return this;
            }

            public Builder keepAliveSeconds(long keepAliveSeconds) {
                if (keepAliveSeconds >= 0) {
                    this.keepAliveSeconds = keepAliveSeconds;
                }
                return this;
            }

            public Builder threadNamePrefix(String threadNamePrefix) {
                if (StringUtils.isNotBlank(threadNamePrefix)) {
                    this.threadNamePrefix = threadNamePrefix;
                }
                return this;
            }

            public Builder daemon(boolean daemon) {
                this.daemon = daemon;
                return this;
            }

            public Builder allowCoreThreadTimeout(boolean allowCoreThreadTimeout) {
                this.allowCoreThreadTimeout = allowCoreThreadTimeout;
                return this;
            }

            public PoolConfig build() {
                if (maxSize < coreSize) {
                    maxSize = coreSize;
                }
                return new PoolConfig(this);
            }
        }
    }

    public static ThreadPoolConfig fromYaml(Map<String, ?> yamlSection) {
        Map<String, String> overrides = new HashMap<>(System.getenv());
        System.getProperties().forEach((key, value) -> overrides.put(String.valueOf(key), String.valueOf(value)));
        return from(yamlSection, overrides);
    }
}

