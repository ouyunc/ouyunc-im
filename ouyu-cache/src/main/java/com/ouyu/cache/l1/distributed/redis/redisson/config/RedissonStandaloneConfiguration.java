package com.ouyu.cache.l1.distributed.redis.redisson.config;

import cn.hutool.core.util.StrUtil;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonStandaloneProperties;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 单例配置
 * @Version V1.0
 **/
public class RedissonStandaloneConfiguration {

    private RedissonStandaloneProperties redissonStandaloneProperties = ConfigFactory.create(RedissonStandaloneProperties.class);

    /**
     * @Author fangzhenxun
     * @Description 配置
     * @return org.redisson.config.Config
     */
    public Config redissonConfig() {
        String nodes = redissonStandaloneProperties.nodes();
        CharSequence password = redissonStandaloneProperties.password();
        Config config = new Config();
        SingleServerConfig singleServerConfig = config.useSingleServer()
                .setDatabase(redissonStandaloneProperties.database())
                .setAddress(nodes);
        if (StrUtil.isNotBlank(password)) {
            singleServerConfig.setPassword(this.redissonStandaloneProperties.password());
        }
        return config;
    }


    /**
     * @Author fangzhenxun
     * @Description  获取RedissonClient
     * @return org.redisson.api.RedissonClient
     */
    public RedissonClient redissonClient () {
        return Redisson.create(redissonConfig());
    }
}
