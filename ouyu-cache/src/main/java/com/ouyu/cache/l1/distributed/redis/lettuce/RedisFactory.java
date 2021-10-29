package com.ouyu.cache.l1.distributed.redis.lettuce;

import cn.hutool.core.lang.Singleton;
import com.ouyu.cache.l1.distributed.redis.lettuce.builder.RedisBuilder;
import com.ouyu.cache.l1.distributed.redis.lettuce.builder.impl.RedisTemplateBuilder;
import com.ouyu.cache.l1.distributed.redis.lettuce.builder.impl.StringRedisTemplateBuilder;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fangzhenxun
 * @Description: 单例模式
 * @Version V1.0
 **/
public class RedisFactory {


    //每一个数据库只有一个redisTemplate 实例
    private static volatile ConcurrentHashMap<Integer, RedisTemplate> redisTemplateMap = new ConcurrentHashMap<>();
    private static volatile ConcurrentHashMap<Integer, StringRedisTemplate> stringRedisTemplateMap = new ConcurrentHashMap<>();
    private RedisFactory() {
    }


    public static RedisTemplate redisTemplate() {
        return redisTemplate(0);
    }

    public static RedisTemplate redisTemplate(int database) {
        if (redisTemplateMap.get(database) == null) {
            synchronized (ConcurrentHashMap.class) {
                if (redisTemplateMap.get(database) == null){
                    RedisBuilder<RedisTemplate> redisBuilder = new RedisTemplateBuilder();
                    redisTemplateMap.put(database, redisBuilder.build(database));
                }
            }
        }
        return redisTemplateMap.get(database);
    }


    public static StringRedisTemplate stringRedisTemplate() {
        return stringRedisTemplate(0);
    }

    public static StringRedisTemplate stringRedisTemplate(int database) {
        if (stringRedisTemplateMap.get(database) == null) {
            synchronized (ConcurrentHashMap.class) {
                if (stringRedisTemplateMap.get(database) == null){
                    RedisBuilder<StringRedisTemplate> redisBuilder = new StringRedisTemplateBuilder();
                    stringRedisTemplateMap.put(database, redisBuilder.build(database));
                }
            }
        }
        return stringRedisTemplateMap.get(database);
    }

}
