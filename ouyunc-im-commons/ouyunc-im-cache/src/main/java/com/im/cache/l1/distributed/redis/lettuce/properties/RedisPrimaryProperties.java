package com.im.cache.l1.distributed.redis.lettuce.properties;

import com.im.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import org.aeonbits.owner.Config;

/**
 * @Author fangzhenxun
 * @Description: redis 核心配置
 **/
@Config.LoadPolicy(Config.LoadType.FIRST)
@Config.Sources({"classpath:ouyunc-im-cache.properties","classpath:ouyunc-im-server.properties","classpath:ouyunc-im-client.properties","classpath:ouyunc-im.properties"})
public interface RedisPrimaryProperties extends Config {

    /**
     * redis 密码
     **/
    @Key("cache.redis.primary")
    RedisEnum primary();
}
