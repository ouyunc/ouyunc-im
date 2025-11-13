package com.ouyunc.base.executor;

/**
 * 线程池id
 */
public enum ThreadPoolId {
    MESSAGE_SEND("message-send"),
    MESSAGE_PROCESSOR("message-processor"),
    QOS_TASK("qos-task"),
    ROUTER("router"),
    REPOSITORY("repository"),
    EVENT_LISTENER("event-listener"),
    CLUSTER_CLIENT_HEARTBEAT("cluster-client-heartbeat"),
    SYSTEM_CLOCK("system-clock"),
    REDIS_PUBSUB("redis-pubsub");

    private final String configKey;

    ThreadPoolId(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigKey() {
        return configKey;
    }

    public static ThreadPoolId fromConfigKey(String key) {
        for (ThreadPoolId id : values()) {
            if (id.configKey.equalsIgnoreCase(key)) {
                return id;
            }
        }
        return null;
    }
}

