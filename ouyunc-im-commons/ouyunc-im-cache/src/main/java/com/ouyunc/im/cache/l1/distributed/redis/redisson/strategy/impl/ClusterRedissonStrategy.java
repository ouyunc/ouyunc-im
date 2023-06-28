package com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.impl;


import com.ouyunc.im.cache.l1.distributed.redis.redisson.enums.RedisEnum;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.properties.ClusterRedissonProperties;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.strategy.RedissonStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.springframework.util.StringUtils;

/**
 * @author fangzhenxun
 * @date 2020/1/13 16:24
 * @description redisson 的集群配置 （待测试）
 */
public class ClusterRedissonStrategy implements RedissonStrategy {


    private static final ClusterRedissonProperties clusterRedissonProperties;

    static {
        clusterRedissonProperties = ConfigFactory.create(ClusterRedissonProperties.class);
    }

    /**
     * @author fangzhenxun
     * @description 标识该策略类是集群
     * @date  2020/1/13 16:24
     * @param
     * @return com.xyt.cache.config.redis.redisson.enums.RedisEnum
     **/
    @Override
    public RedisEnum getType() {
        return RedisEnum.CLUSTER;
    }


    /**
     * @author fangzhenxun
     * @description
     * @date  2020/1/13 16:25
     * @param
     * @return org.redisson.config.Config
     **/
    @Override
    public Config buildConfig(int database) {
        Config config = new Config();
        ClusterServersConfig clusterServersConfig = config.useClusterServers()
                .addNodeAddress(clusterRedissonProperties.nodes())
                .setScanInterval(clusterRedissonProperties.scanInterval())
                .setIdleConnectionTimeout(clusterRedissonProperties.soTimeout())
                .setConnectTimeout(clusterRedissonProperties.connTimeout())
                .setTimeout(clusterRedissonProperties.timeout())
                .setRetryAttempts(clusterRedissonProperties.retryAttempts())
                .setRetryInterval(clusterRedissonProperties.retryInterval())
                .setMasterConnectionPoolSize(clusterRedissonProperties.pollSize())
                .setSlaveConnectionPoolSize(clusterRedissonProperties.pollSize());

        //如果密码不为空则设置密码
        if (!StringUtils.hasLength(clusterRedissonProperties.password())) {
            clusterServersConfig.setPassword(clusterRedissonProperties.password());
        }
        return config;
    }
}
