package com.ouyu.cache.l1.distributed.redis.redisson.strategy.impl;


import com.ouyu.cache.l1.distributed.redis.redisson.enums.RedisEnum;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.SentinelRedissonProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.strategy.RedissonStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.springframework.util.StringUtils;

/**
 * @author fangzhenxun
 * @date 2020/1/13 14:39
 * @description redisson哨兵模式的策略配置
 */
public class SentinelRedissonStrategy implements RedissonStrategy {

    /**
     * redisson哨兵配置信息
     **/
    private static final SentinelRedissonProperties sentinelRedissonProperties;

    static {
        sentinelRedissonProperties = ConfigFactory.create(SentinelRedissonProperties.class);
    }

    /**
     * @param
     * @return com.xyt.cache.config.redis.lettuce.enums.RedisEnum
     * @author fangzhenxun
     * @description redisson 标识该策略是哨兵模式的策略
     * @date 2020/1/13 14:43
     **/
    @Override
    public RedisEnum getType() {
        return RedisEnum.SENTINEL;
    }

    /**
     * @param
     * @return org.redisson.config.Config
     * @author fangzhenxun
     * @description redisson哨兵模式的构建
     * @date 2020/1/13 14:46
     **/
    @Override
    public Config buildConfig(int database) {
        Config config = new Config();
        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setDatabase(sentinelRedissonProperties.database())
                .setMasterName(sentinelRedissonProperties.master())
                .addSentinelAddress(sentinelRedissonProperties.nodes())
                //设置只读节点
                .setReadMode(ReadMode.SLAVE)
                .setConnectTimeout(sentinelRedissonProperties.connTimeout())
                .setTimeout(sentinelRedissonProperties.timeout())
                .setIdleConnectionTimeout(sentinelRedissonProperties.soTimeout())
                .setMasterConnectionMinimumIdleSize(sentinelRedissonProperties.pollMinIdle())
                .setMasterConnectionPoolSize(sentinelRedissonProperties.pollSize())
                .setSlaveConnectionPoolSize(sentinelRedissonProperties.pollSize());
        //如果密码不为空则设置密码
        if (!StringUtils.hasLength(sentinelRedissonProperties.password())) {
            sentinelServersConfig.setPassword(sentinelRedissonProperties.password());
        }
        if (0 <= database && database <= 16) {
            sentinelServersConfig.setDatabase(database);
        }
        return config;
    }
}
