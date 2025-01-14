package com.ouyunc.base.constant;

/**
 * 属性常量类
 */
public class PropertiesConfigConstant {

    /**
     * 配置文件路径，从resources 目录下加载
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
     * mq kafka 配置属性前缀
     */
    public static final String  KAFKA_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq.kafka";

    /**
     * mq kafka 单实例配置属性前缀
     */
    public static final String  KAFKA_STANDALONE_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq.kafka.standalone";

    /**
     * mq kafka 集群 配置属性前缀
     */
    public static final String  KAFKA_CLUSTER_CONFIG_PROPERTIES_PREFIX = "ouyunc.mq.kafka.cluster";
}
