package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.constant.ModelEnum;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.redisson.config.ClusterServersConfig;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * @author fzx
 * @description 集群的redis的配置，目前没有搭建集群环境，测试不通
 */
public class ClusterRedisStrategy extends AbstractRedisStrategy {


    /**
     * @author fzx
     * @description  集群模式类型
     **/
    @Override
    public ModelEnum getModel() {
        return ModelEnum.CLUSTER;
    }

    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration(redisProperties.getCluster().getNodes());
        redisClusterConfiguration.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisClusterConfiguration.setPassword(redisProperties.getPassword());
        }
        return redisClusterConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        List<String> nodes = redisProperties.getCluster().getNodes();
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        ClusterServersConfig clusterServersConfig = config.useClusterServers()
                .addNodeAddress(nodes.toArray(new String[0]))
                .setConnectTimeout((int)redisProperties.getConnectTimeout().toMillis())
                .setTimeout((int)redisProperties.getTimeout().toMillis())
                .setMasterConnectionPoolSize(pool.getMaxIdle())
                .setSlaveConnectionPoolSize(pool.getMaxIdle());
        //如果密码不为空则设置密码
        if (!StringUtils.hasLength(redisProperties.getPassword())) {
            clusterServersConfig.setPassword(redisProperties.getPassword());
        }
        return config;
    }

}
