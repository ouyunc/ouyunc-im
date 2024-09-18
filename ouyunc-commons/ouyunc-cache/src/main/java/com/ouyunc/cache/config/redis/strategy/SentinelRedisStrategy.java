package com.ouyunc.cache.config.redis.strategy;


import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.redisson.config.SentinelServersConfig;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisSentinelConfiguration;
import org.springframework.util.StringUtils;

import java.util.HashSet;

/**
 * @author fzx
 * @description 哨兵模式的redis的配置
 */
public class SentinelRedisStrategy extends AbstractRedisStrategy {



    /**
     * @author fzx
     * @description  哨兵模式类型
     **/
    @Override
    public ModelEnum getModel() {
        return ModelEnum.SENTINEL;
    }


    /**
     * 构造不同的redis配置
     */
    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisSentinelConfiguration redisSentinelConfiguration = new RedisSentinelConfiguration(redisProperties.getSentinel().getMaster(), new HashSet<>(redisProperties.getSentinel().getNodes()));
        redisSentinelConfiguration.setDatabase(redisProperties.getDatabase());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisSentinelConfiguration.setPassword(redisProperties.getPassword());
        }
        if (0 <= database && database <= 16) {
            redisSentinelConfiguration.setDatabase(database);
        }
        return redisSentinelConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        SentinelServersConfig sentinelServersConfig = config.useSentinelServers()
                .setDatabase(redisProperties.getDatabase())
                .setMasterName(redisProperties.getSentinel().getMaster())
                .addSentinelAddress(redisProperties.getSentinel().getNodes().toArray(new String[0]))
                //设置只读节点
                .setReadMode(ReadMode.SLAVE)
                .setConnectTimeout((int)redisProperties.getConnectTimeout().toMillis())
                .setTimeout((int)redisProperties.getTimeout().toMillis())
                .setMasterConnectionMinimumIdleSize(pool.getMinIdle())
                .setMasterConnectionPoolSize(pool.getMaxIdle())
                .setSlaveConnectionPoolSize(pool.getMaxIdle());
        //如果密码不为空则设置密码
        if (!StringUtils.hasLength(redisProperties.getPassword())) {
            sentinelServersConfig.setPassword(redisProperties.getPassword());
        }
        if (0 <= database && database <= 16) {
            sentinelServersConfig.setDatabase(database);
        }
        return config;
    }

}
