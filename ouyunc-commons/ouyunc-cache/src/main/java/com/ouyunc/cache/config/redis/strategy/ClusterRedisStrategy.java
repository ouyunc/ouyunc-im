package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.constant.ModeEnum;
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
    public ModeEnum getModel() {
        return ModeEnum.CLUSTER;
    }

    @Override
    public RedisConfiguration redisConfiguration(int database) {
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration(redisProperties.getCluster().getNodes());
        redisClusterConfiguration.setMaxRedirects(redisProperties.getCluster().getMaxRedirects());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(redisProperties.getPassword())) {
            redisClusterConfiguration.setPassword(redisProperties.getPassword());
        }
        if (StringUtils.hasLength(redisProperties.getUsername())) {
            redisClusterConfiguration.setUsername(redisProperties.getUsername());
        }
        return redisClusterConfiguration;
    }

    @Override
    public Config redissonConfiguration(int database) {
        Config config = new Config();
        List<String> nodes = getFormattedNodes(redisProperties.getCluster().getNodes());
        RedisProperties.Pool pool = redisProperties.getLettuce().getPool();
        ClusterServersConfig clusterServersConfig = config.useClusterServers()
                .addNodeAddress(nodes.toArray(new String[0]))
                // 使用抽象类超时兜底，避免 NPE
                .setConnectTimeout((int) getConnectTimeout().toMillis())
                .setTimeout((int) getTimeout().toMillis())
                .setMasterConnectionPoolSize(pool.getMaxIdle())
                .setSlaveConnectionPoolSize(pool.getMaxIdle());

        config.setTcpKeepAlive(true);
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
