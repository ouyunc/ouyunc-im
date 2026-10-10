package com.ouyunc.base.constant;

/**
 * 属性常量类
 */
public class PropertiesConfigConstant {

    /**
     * 配置文件名。classpath 上的这份是默认层；
     * 进程启动后实际生效的是 ConfigBootstrap 合并进 ConfigRegistry 的结果。
     */
    public static final String GLOBAL_CONFIG_FILE_LOCATION = "ouyunc-server.yml";

    /**
     * 缓存配置属性前缀
     */
    public static final String CACHE_CONFIG_PROPERTIES_PREFIX = "ouyunc.cache.redis";

    /**
     * jdbc配置属性前缀
     */
    public static final String JDBC_CONFIG_PROPERTIES_PREFIX = "ouyunc.db.jdbc";

    /**
     * mongodb配置属性前缀
     */
    public static final String  MONGODB_CONFIG_PROPERTIES_PREFIX = "ouyunc.db.mongo";
    /**
     * influxdb配置属性前缀
     */
    public static final String  INFLUX_CONFIG_PROPERTIES_PREFIX = "ouyunc.db.influx";

    /**
     * mq 顶层配置属性前缀（含 type 等）
     */
    public static final String MQ_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq";

    /**
     * mq kafka 配置属性前缀
     */
    public static final String  KAFKA_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq.kafka";

    /**
     * mq rocket 配置属性前缀
     */
    public static final String ROCKET_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq.rocket";

}
