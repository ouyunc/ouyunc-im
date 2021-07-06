package com.ouyu.cache.l1.distributed.redis.properties;

import com.ouyu.cache.constant.enums.RedisEnum;
import org.aeonbits.owner.Config;

/**
 * @Author fangzhenxun
 * @Description: redis 全局主配置文件
 * @Version V1.0
 **/
@Config.Sources({"classpath:ouyu-cache.properties"})
public interface RedisProperties extends Config {

    /**
     * redis j
     */
    @Key("cache.redis.primary")
    @DefaultValue("STANDALONE")
    RedisEnum primary();
}
