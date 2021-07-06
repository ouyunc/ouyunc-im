package com.ouyu.cache.l1.distributed.redis.lettuce.properties;


import org.aeonbits.owner.Config;
import org.checkerframework.checker.units.qual.C;

/**
 * @Author fangzhenxun
 * @Description 单例配置文件
 */
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface LettuceStandaloneProperties extends Config {
    /**
     * 主机ip+端口号
     */
    @Config.Key("cache.redis.standalone.lettuce.database")
    @DefaultValue("0")
    int database();

    /**
     * 主机ip+端口号
     */
    @Config.Key("cache.redis.standalone.lettuce.nodes")
    @DefaultValue("127.0.0.1:6379")
    String nodes();

    /**
     * 密码
     */
    @Config.Key("cache.redis.standalone.lettuce.password")
    String password();

}