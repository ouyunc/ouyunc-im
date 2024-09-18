package com.ouyunc.cache.config.redis.builder;


import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description redissonClient的具体建造者
 */
public class RedissonClientBuilder extends AbstractRedisBuilder<RedissonClient> {
    private static final Logger log = LoggerFactory.getLogger(RedissonClientBuilder.class);


    /**
     * @author fzx
     * @description  RedissonClient 的建造方法
     **/
    @Override
    public RedissonClient build(int database) {
        RedisStrategy redissonStrategy = currentRedissonStrategy();
        Config config = redissonStrategy.buildRedissonConfig(database, redisProperties);
        return Redisson.create(config);
    }


    /**
     * @author fzx
     * @description  配置当前redisson的策略
     **/
    private RedisStrategy currentRedissonStrategy() {
        if (!redisStrategyList.isEmpty()) {
            return redisStrategyList.parallelStream().filter(redissonStrategy -> {
                ModelEnum redisModel = redissonStrategy.getModel();
                if (mode.equals(redisModel)) {
                    log.info("当前redisClient加载模式为========》" + redissonStrategy.getModel().getRedisModel());
                }
                return mode.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式"));
        }
        throw new RuntimeException(  "没有找到对应的配置方式");
    }

}
