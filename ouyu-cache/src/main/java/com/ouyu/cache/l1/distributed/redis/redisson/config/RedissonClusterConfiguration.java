package com.ouyu.cache.l1.distributed.redis.redisson.config;


import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonClusterProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonSentinelProperties;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 集群模式配置
 */
public class RedissonClusterConfiguration {

    private RedissonClusterProperties redissonClusterProperties = ConfigFactory.create(RedissonClusterProperties.class);


    /**
     * @Author fangzhenxun
     * @Description 配置
     * @return org.redisson.config.Config
     */
    public Config redissonConfig() {
        Set<String> nodes = redissonClusterProperties.nodes();
        String[] nodeArr = redissonClusterProperties.nodes().toArray(new String[nodes.size()]);
        Config config = new Config();
        config.useClusterServers()
                .setScanInterval(2000)
                .setPassword(redissonClusterProperties.password())
                .addNodeAddress(nodeArr);
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