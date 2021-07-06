package com.ouyu.cache.l1.distributed.redis.redisson.properties;


import org.aeonbits.owner.Config;

/**
 * @Author fangzhenxun
 * @Description 单例配置文件
 */
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface RedissonStandaloneProperties extends Config {

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.standalone.redisson.database")
    @DefaultValue("0")
    int database();

    /**
     * 主机ip+端口号
     */
    @Key("cache.redis.standalone.redisson.nodes")
    @DefaultValue("127.0.0.1:6379")
    String nodes();

    /**
     * 密码
     */
    @Key("cache.redis.standalone.redisson.password")
    String password();
}