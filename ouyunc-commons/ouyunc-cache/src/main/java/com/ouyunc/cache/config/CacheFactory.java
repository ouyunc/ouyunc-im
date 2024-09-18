package com.ouyunc.cache.config;

import com.ouyunc.cache.config.redis.builder.AbstractRedisBuilder;
import com.ouyunc.cache.config.redis.builder.RedisTemplateBuilder;
import com.ouyunc.cache.config.redis.builder.RedissonClientBuilder;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.api.RedissonClient;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fzx
 * @Description: 单例模式
 **/
public enum CacheFactory {

    // redis
    REDIS {
        //每一个数据库只有一个redisTemplate 实例
        private static final ConcurrentHashMap<Integer, RedisTemplate<?,?>> redisTemplateMap = new ConcurrentHashMap<>();
        private static RedisProperties existRedisProperties;
        @Override
        public <T> T instance(RedisProperties ...redisProperties) {
            return instance(0, redisProperties);
        }
        @SuppressWarnings("unchecked")
        @Override
        public <T> T instance(int database,RedisProperties ...redisProperties) {
            if (redisTemplateMap.get(database) == null) {
                synchronized (ConcurrentHashMap.class) {
                    if (redisTemplateMap.get(database) == null) {
                        AbstractRedisBuilder<RedisTemplate<?,?>> redisBuilder = new RedisTemplateBuilder();
                        if (redisProperties != null && redisProperties.length > 0) {
                            redisBuilder.setRedisProperties(redisProperties[0]);
                            if (existRedisProperties == null) {
                                existRedisProperties = redisProperties[0];
                            }
                        }else if (existRedisProperties != null) {
                            // 从本地获取
                            redisBuilder.setRedisProperties(existRedisProperties);
                        }
                        redisTemplateMap.put(database, redisBuilder.build(database));
                    }
                }
            }
            return (T) redisTemplateMap.get(database);
        }
    },

    // redisson
    REDISSON {
        private static final ConcurrentHashMap<Integer, RedissonClient> redissonClientMap = new ConcurrentHashMap<>();
        private static RedisProperties existRedisProperties;
        @SuppressWarnings("unchecked")
        @Override
        public <T> T instance(int database,RedisProperties ...redisProperties) {
            if (redissonClientMap.get(database) == null) {
                synchronized (ConcurrentHashMap.class) {
                    if (redissonClientMap.get(database) == null) {
                        AbstractRedisBuilder<RedissonClient> redissonBuilder = new RedissonClientBuilder();
                        if (redisProperties != null && redisProperties.length > 0) {
                            redissonBuilder.setRedisProperties(redisProperties[0]);
                            if (existRedisProperties == null) {
                                existRedisProperties = redisProperties[0];
                            }
                        }else if (existRedisProperties != null) {
                            redissonBuilder.setRedisProperties(existRedisProperties);
                        }
                        redissonClientMap.put(database, redissonBuilder.build(database));
                    }
                }
            }
            return (T) redissonClientMap.get(database);
        }

        @Override
        public <T> T instance(RedisProperties ...redisProperties) {
            return instance(0, redisProperties);
        }
    };


    /**
     * @Author fzx
     * @Description 获取实例
     */
    public abstract <T> T instance(int database, RedisProperties ...redisProperties);

    /**
     * @Author fzx
     * @Description 获取实例
     */
    public abstract <T> T instance(RedisProperties ...redisProperties);
}
