package com.ouyunc.cache.config.redis.builder;


import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.strategy.RedisStrategy;
import org.redisson.Redisson;
import org.redisson.api.RedissonReactiveClient;
import org.redisson.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 响应式 reactiveRedissonClient的具体建造者
 */
public class ReactiveRedissonClientBuilder extends AbstractRedisBuilder<RedissonReactiveClient> {
    private static final Logger log = LoggerFactory.getLogger(ReactiveRedissonClientBuilder.class);


    /**
     * @author fzx
     * @description  reactiveRedissonClient 的建造方法
     **/
    @Override
    public RedissonReactiveClient build(int database) {
        RedisStrategy redissonStrategy = currentRedissonStrategy();
        Config config = redissonStrategy.buildRedissonConfig(database, redisProperties);
        return Redisson.create(config).reactive();
    }


    /**
     * @author fzx
     * @description  配置当前redisson的策略
     **/
    private RedisStrategy currentRedissonStrategy() {
        if (!redisStrategyList.isEmpty()) {
            return redisStrategyList.parallelStream().filter(redissonStrategy -> {
                ModeEnum redisModel = redissonStrategy.getModel();
                if (mode.equals(redisModel)) {
                    log.info("当前reactiveRedisClient加载模式为========》 {}" , redissonStrategy.getModel().getRedisModel());
                }
                return mode.equals(redisModel);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式"));
        }
        throw new RuntimeException(  "没有找到对应的配置方式");
    }

}
