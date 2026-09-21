package com.ouyunc.base.executor;

/**
 * 线程池id
 */
public enum ThreadPoolId {
    MESSAGE_SEND("message-send"),
    MESSAGE_PROCESSOR("message-processor"),
    QOS_TASK("qos-task"),
    QOS_CONTROL("qos-control"),
    /** 节点租约心跳 / 连接数发布，与 QoS 重试隔离，避免业务池打满拖死路由 */
    NODE_LEASE("node-lease"),
    ROUTER("router"),
    REPOSITORY("repository"),
    /** 消息 Redis 持久化，与 JDBC/MQ 仓库任务隔离。 */
    REDIS_PERSISTENCE("redis-persistence"),
    EVENT_LISTENER("event-listener"),
    CLUSTER_CLIENT_HEARTBEAT("cluster-client-heartbeat"),
    SYSTEM_CLOCK("system-clock"),
    /** HTTP 推送 preProcess / 受理（不占用 HTTP 业务线程） */
    HTTP_PUSH_VERIFY("http-push-verify");

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



