package com.ouyu.cache.l1.distributed.redis.redisson.properties;


import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 集群
 */
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface RedissonClusterProperties extends Config {

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.cluster.redisson.database")
    @DefaultValue("0")
    int database();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.cluster.redisson.nodes")
    @DefaultValue("127.0.0.1:6379,127.0.0.1:6380")
    Set<String> nodes();

    /**
     * 密码
     */
    @Key("cache.redis.cluster.redisson.password")
    String password();

}