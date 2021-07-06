package com.ouyu.cache.l1.distributed.redis.redisson.config;


import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonSentinelProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonStandaloneProperties;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 哨兵模式配置
 */
public class RedissonSentinelConfiguration {

    private RedissonSentinelProperties redissonSentinelProperties = ConfigFactory.create(RedissonSentinelProperties.class);


    /**
     * @Author fangzhenxun
     * @Description 配置
     * @param
     * @return org.redisson.config.Config
     */
    public Config redissonConfig() {
        Set<String> nodes = redissonSentinelProperties.nodes();
        String[] nodeArr = redissonSentinelProperties.nodes().toArray(new String[nodes.size()]);
        Config config = new Config();
         config.useSentinelServers()
                .setMasterName(redissonSentinelProperties.masterId())
                .addSentinelAddress(nodeArr)
                .setPassword(redissonSentinelProperties.password());
        return config;
    }


    /**
     * @Author fangzhenxun
     * @Description  获取RedissonClient
     * @param
     * @return org.redisson.api.RedissonClient
     */
    public RedissonClient redissonClient () {
        return Redisson.create(redissonConfig());
    }
}