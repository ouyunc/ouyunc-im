package com.ouyunc.im.cache.l1.distributed.redis.redisson.builder.impl;


import com.ouyunc.im.cache.l1.distributed.redis.redisson.builder.RedissonBuilder;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.enums.RedisEnum;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.properties.RedisPrimaryProperties;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.RedissonStrategy;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.impl.ClusterRedissonStrategy;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.impl.SentinelRedissonStrategy;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.impl.StandaloneRedissonStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * @author fangzhenxun
 * @date 2020/1/10 15:08
 * @description redissonClient的具体建造者
 */
public class RedissonClientBuilder implements RedissonBuilder<RedissonClient> {
    private static Logger log = LoggerFactory.getLogger(RedissonClientBuilder.class);


    /**
     * 获取当前选中的redis使用模式类型，如果没有设置primary则默认为单例模式类型
     **/
    private static final RedisEnum type;

    /**
     * 获取所有redisson的模式策略
     **/
    private static final List<RedissonStrategy> redissonStrategyList;

    /**
     * 构建锁
     */
    private static final Lock LOCK ;

    /**
     * 初始化数据
     **/
    static {
        type = ConfigFactory.create(RedisPrimaryProperties.class).primary();
        redissonStrategyList = new ArrayList<>();
        redissonStrategyList.add(new StandaloneRedissonStrategy());
        redissonStrategyList.add(new SentinelRedissonStrategy());
        redissonStrategyList.add(new ClusterRedissonStrategy());
        LOCK = new ReentrantLock();
    }



    /**
     * @author fangzhenxun
     * @description  RedissonClient 的建造方法
     * @date  2020/1/10 15:10
     * @param
     * @return java.lang.Object
     **/
    @Override
    public RedissonClient build(int database) {
        RedissonClient redissonClient = null;
        try {
            if (redissonClient == null) {
                LOCK.tryLock(10, TimeUnit.MILLISECONDS);
                RedissonStrategy redissonStrategy = currentRedissonStrategy();
                Config config = redissonStrategy.buildConfig(database);
                redissonClient = Redisson.create(config);
            }
        } catch (Exception e) {
            log.error("redisson 配置模版失败->{}" ,e.getMessage());
        } finally {
            //释放锁
            LOCK.unlock();
        }
        return redissonClient;
    }


    /**
     * @author fangzhenxun
     * @description  配置当前redisson的策略
     * @date  2020/1/10 17:19
     * @param
     * @return com.xyt.cache.config.redis.redisson.strategy.RedissonStrategy
     **/
    private RedissonStrategy currentRedissonStrategy() {
        if (!redissonStrategyList.isEmpty()) {
            return redissonStrategyList.parallelStream().filter(redissonStrategy -> {
                RedisEnum redisModel = redissonStrategy.getType();
                if (type.equals(redisModel)) {
                    log.info("当前redisClient加载模式为========》" + redissonStrategy.getType().getRedisModel());
                }
                return type.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式"));
        }
        throw new RuntimeException(  "没有找到对应的配置方式");
    }

}
