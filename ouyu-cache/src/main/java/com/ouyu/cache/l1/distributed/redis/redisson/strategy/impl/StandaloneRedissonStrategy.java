package com.ouyu.cache.l1.distributed.redis.redisson.strategy.impl;


import com.ouyu.cache.l1.distributed.redis.redisson.enums.RedisEnum;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.StandaloneRedissonProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.strategy.RedissonStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.util.StringUtils;

/**
 * @author fangzhenxun
 * @date 2020/1/10 17:09
 * @description 单例redisson具体策略实现类
 */
public class StandaloneRedissonStrategy implements RedissonStrategy {

    private static final StandaloneRedissonProperties standaloneRedissonProperties;

    static {
        standaloneRedissonProperties = ConfigFactory.create(StandaloneRedissonProperties.class);
    }
    /**
     * @author fangzhenxun
     * @description 标识该类是某种redisson策略
     * @date  2020/1/10 17:20
     * @param
     * @return com.xyt.cache.config.redis.lettuce.enums.RedisEnum
     **/
    @Override
    public RedisEnum getType() {
        return RedisEnum.STANDALONE;
    }

    /**
     * @author fangzhenxun
     * @description  创建redisson的配置类
     * @date  2020/1/10 17:22
     * @param
     * @return org.redisson.config.Config
     **/
    @Override
    public Config buildConfig(int database) {
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + standaloneRedissonProperties.host() + ":" + standaloneRedissonProperties.port())
                .setDatabase(standaloneRedissonProperties.database())
                .setConnectTimeout(standaloneRedissonProperties.connTimeout())
                .setTimeout(standaloneRedissonProperties.timeout())
                .setIdleConnectionTimeout(standaloneRedissonProperties.soTimeout())
                .setConnectionMinimumIdleSize(standaloneRedissonProperties.pollMinIdle())
                .setConnectionPoolSize(standaloneRedissonProperties.pollSize());
        //如果密码不为空则设置密码
        if (!StringUtils.hasLength(standaloneRedissonProperties.password())) {
            singleServerConfig.setPassword(standaloneRedissonProperties.password());
        }
        if (0 <= database && database <= 16) {
            singleServerConfig.setDatabase(database);
        }
        return config;
    }
}
