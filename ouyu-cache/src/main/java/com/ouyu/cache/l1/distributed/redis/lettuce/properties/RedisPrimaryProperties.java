package com.ouyu.cache.l1.distributed.redis.lettuce.properties;

import com.ouyu.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import org.aeonbits.owner.Config;

/**
 * @Author fangzhenxun
 * @Description: redis 核心配置
 * @Version V1.0
 **/
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface RedisPrimaryProperties extends Config {

    /**
     * redis 密码
     **/
    @Key("cache.redis.primary")
    RedisEnum primary();
}