package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.constant.ModeEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author fzx
 * @description 单例redis配置,作为默认策略(至少有一个生效)
 */
public class StandaloneRedisStrategy extends AbstractRedisStrategy {

    /**
     * @author fzx
     * @description  单例模式类型
     **/
    @Override
    public ModeEnum getModel() {
        return ModeEnum.STANDALONE;
    }


    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setPort(redisProperties.getPort());
        redisStandaloneConfiguration.setHostName(redisProperties.getHost());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisStandaloneConfiguration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        if (StringUtils.hasLength(redisProperties.getUsername())) {
            redisStandaloneConfiguration.setUsername(redisProperties.getUsername());
        }
        redisStandaloneConfiguration.setDatabase(database);
        return redisStandaloneConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        config.useSingleServer()
                .setAddress(getFormattedNodes(List.of(redisProperties.getHost() + ":" + redisProperties.getPort())).getFirst())
                .setDatabase(database)
                // 使用抽象类超时兜底，避免 NPE
                .setConnectTimeout((int) getConnectTimeout().toMillis())
                .setTimeout((int) getTimeout().toMillis())
                .setConnectionMinimumIdleSize(pool.getMinIdle())
                .setConnectionPoolSize(pool.getMaxIdle());
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
