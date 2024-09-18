package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.util.StringUtils;

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
    public ModelEnum getModel() {
        return ModelEnum.STANDALONE;
    }


    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setPort(redisProperties.getPort());
        redisStandaloneConfiguration.setDatabase(redisProperties.getDatabase());
        redisStandaloneConfiguration.setHostName(redisProperties.getHost());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisStandaloneConfiguration.setPassword(RedisPassword.of(redisProperties.getPassword()));
        }
        if (0 <= database && database <= 16) {
            redisStandaloneConfiguration.setDatabase(database);
        }
        return redisStandaloneConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setAddress("redis://" + redisProperties.getHost() + ":" + redisProperties.getPort())
                .setDatabase(redisProperties.getDatabase())
                .setConnectTimeout((int)redisProperties.getConnectTimeout().toMillis())
                .setTimeout((int)redisProperties.getTimeout().toMillis())
                .setConnectionMinimumIdleSize(pool.getMinIdle())
                .setConnectionPoolSize(pool.getMaxIdle());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            singleServerConfig.setPassword(redisProperties.getPassword());
        }
        if (0 <= database && database <= 16) {
            singleServerConfig.setDatabase(database);
        }
        return config;
    }

}
