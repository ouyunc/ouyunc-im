package com.im.cache.l1.distributed.redis.lettuce;

import com.im.cache.l1.distributed.redis.lettuce.builder.RedisBuilder;
import com.im.cache.l1.distributed.redis.lettuce.builder.impl.RedisTemplateBuilder;
import com.im.cache.l1.distributed.redis.lettuce.builder.impl.StringRedisTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author fangzhenxun
 * @Description: 单例模式
 **/
public enum RedisFactory {
    INSTANCE;

    private final Lock lock = new ReentrantLock();

    private static volatile ConcurrentHashMap<Integer, RedisTemplate> redisTemplateMap = new ConcurrentHashMap<>();

    public RedisTemplate redisTemplate() {
        return redisTemplate(0);
    }

    public RedisTemplate redisTemplate(int database) {
        if (redisTemplateMap.get(database) == null) {
            lock.lock();
            try{
                if (redisTemplateMap.get(database) == null){
                    RedisBuilder<RedisTemplate> redisBuilder = new RedisTemplateBuilder();
                    redisTemplateMap.put(database, redisBuilder.build(database));
                }
            }finally {
                lock.unlock();
            }
        }
        return redisTemplateMap.get(database);
    }
}
