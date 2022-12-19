package com.im.cache.l1.distributed.redis.redisson;

import com.im.cache.l1.distributed.redis.redisson.builder.RedissonBuilder;
import com.im.cache.l1.distributed.redis.redisson.builder.impl.RedissonClientBuilder;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentHashMap;

/**
 * @Author fangzhenxun
 * @Description: 单例模式
 * @Version V3.0
 **/
public class RedissonFactory {


    //每一个数据库只有一个RedissonClient 实例
    private static volatile ConcurrentHashMap<Integer, RedissonClient> redissonClientMap = new ConcurrentHashMap<>();

    private RedissonFactory() {
    }


    public static RedissonClient redissonClient() {
        return redissonClient(0);
    }

    public static RedissonClient redissonClient(int database) {
        if (redissonClientMap.get(database) == null) {
            synchronized (ConcurrentHashMap.class) {
                if (redissonClientMap.get(database) == null){
                    RedissonBuilder<RedissonClient> redissonBuilder = new RedissonClientBuilder();
                    redissonClientMap.put(database, redissonBuilder.build(database));
                }
            }
        }
        return redissonClientMap.get(database);
    }



}
