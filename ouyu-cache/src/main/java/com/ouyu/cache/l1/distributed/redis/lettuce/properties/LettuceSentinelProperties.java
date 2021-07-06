package com.ouyu.cache.l1.distributed.redis.lettuce.properties;


import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 哨兵模式
 */
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface LettuceSentinelProperties extends Config  {

    /**
     * 哨兵Master的ID
     */
    @Key("cache.redis.sentinel.lettuce.master")
    @DefaultValue("master")
    String masterId();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.sentinel.lettuce.database")
    @DefaultValue("0")
    int database();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.sentinel.lettuce.nodes")
    @DefaultValue("127.0.0.1:6379,127.0.0.1:6380")
    Set<String> nodes();

    /**
     * 密码
     */
    @Key("cache.redis.sentinel.lettuce.password")
    String password();
}