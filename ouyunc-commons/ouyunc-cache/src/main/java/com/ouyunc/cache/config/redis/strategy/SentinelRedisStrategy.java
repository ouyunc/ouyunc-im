package com.ouyunc.cache.config.redis.strategy;


import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.Config;
import org.redisson.config.ReadMode;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
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
    public ModeEnum getModel() {
        return ModeEnum.SENTINEL;
    }


    /**
     * 构造不同的redis配置
     */
    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisSentinelConfiguration redisSentinelConfiguration = new RedisSentinelConfiguration(redisProperties.getSentinel().getMaster(), new HashSet<>(redisProperties.getSentinel().getNodes()));
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisSentinelConfiguration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        if (StringUtils.hasLength(redisProperties.getUsername())) {
            redisSentinelConfiguration.setUsername(redisProperties.getUsername());
        }
        redisSentinelConfiguration.setDatabase(database);
        return redisSentinelConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        config.useSentinelServers()
                .setDatabase(database)
                .setMasterName(redisProperties.getSentinel().getMaster())
                .addSentinelAddress(getFormattedNodes(redisProperties.getSentinel().getNodes()).toArray(new String[0]))
                //设置只读节点
                .setReadMode(ReadMode.SLAVE)
                // 使用抽象类超时兜底，避免 NPE
                .setConnectTimeout((int) getConnectTimeout().toMillis())
                .setTimeout((int) getTimeout().toMillis())
                .setMasterConnectionMinimumIdleSize(pool.getMinIdle())
                .setMasterConnectionPoolSize(pool.getMaxIdle())
                .setSlaveConnectionPoolSize(pool.getMaxIdle());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            config.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasLength(redisProperties.getUsername())) {
            config.setUsername(redisProperties.getUsername());
        }
        return config;
    }

}
