package com.ouyunc.im.cache.l1.distributed.redis.redisson;

import com.ouyunc.im.cache.l1.distributed.redis.redisson.builder.RedissonBuilder;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.builder.impl.RedissonClientBuilder;
import org.redisson.api.RedissonClient;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @Author fangzhenxun
 * @Description: 单例模式
 **/
public enum RedissonFactory {
    INSTANCE;
    private final Lock lock = new ReentrantLock();

    private static volatile ConcurrentHashMap<Integer, RedissonClient> redissonClientMap = new ConcurrentHashMap<>();

    public RedissonClient redissonClient() {
        return redissonClient(0);
    }

    public RedissonClient redissonClient(int database) {
        if (redissonClientMap.get(database) == null) {
            lock.lock();
            try{
                if (redissonClientMap.get(database) == null){
                    RedissonBuilder<RedissonClient> redissonBuilder = new RedissonClientBuilder();
                    redissonClientMap.put(database, redissonBuilder.build(database));
                }
            }finally {
                lock.unlock();
            }
        }
        return redissonClientMap.get(database);
    }



}
