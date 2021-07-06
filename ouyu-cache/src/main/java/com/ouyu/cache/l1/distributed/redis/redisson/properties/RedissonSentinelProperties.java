package com.ouyu.cache.l1.distributed.redis.redisson.properties;


import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 哨兵模式
 */
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface RedissonSentinelProperties extends Config {

    /**
     * 哨兵Master的ID
     */
    @Key("cache.redis.sentinel.redisson.master")
    @DefaultValue("master")
    String masterId();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.sentinel.redisson.database")
    @DefaultValue("0")
    int database();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.sentinel.redisson.nodes")
    @DefaultValue("127.0.0.1:6379,127.0.0.1:6380")
    Set<String> nodes();

    /**
     * 密码
     */
    @Key("cache.redis.sentinel.redisson.password")
    String password();

}