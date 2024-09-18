package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisConnectionFactory;

/**
 * @author fzx
 * @description redis 抽象策略类
 */
public interface RedisStrategy {

    /**
     * 标识redis实现类的模式类型
     */
    ModelEnum getModel();


    /**
     * @author fzx
     * @description  获得不同策略模式的连接工厂
     **/
    default RedisConnectionFactory buildRedisConnectionFactory(int database, RedisProperties redisProperties) {
        return null;
    }


    /**
     * @author fzx
     * @description  构建redission的配置类
     **/
    default Config buildRedissonConfig(int database, RedisProperties redisProperties) {
        return null;
    }

}
