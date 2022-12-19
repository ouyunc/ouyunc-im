package com.im.cache.l1.distributed.redis.lettuce.strategy.impl;


import com.im.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import com.im.cache.l1.distributed.redis.lettuce.properties.ClusterRedisProperties;
import com.im.cache.l1.distributed.redis.lettuce.strategy.RedisStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author fangzhenxun
 * @date 2020/1/8 15:28
 * @description 集群的redis的配置，目前没有搭建集群环境，测试不通
 */
public class ClusterRedisStrategy implements RedisStrategy {

    private static final ClusterRedisProperties clusterRedisProperties;


    static {
        clusterRedisProperties = ConfigFactory.create(ClusterRedisProperties.class);
    }


    /**
     * @author fangzhenxun
     * @description  集群模式类型
     * @date  2020/1/8 15:40
     * @param
     * @return com.xyt.cache.config.redis.lettuce.enums.RedisEnum
     **/
    @Override
    public RedisEnum getType() {
        return RedisEnum.CLUSTER;
    }

    /**
     * @author fangzhenxun
     * @description  集群redis的配置
     * @date  2020/1/8 15:29
     * @param
     * @return org.springframework.data.redis.core.RedisTemplate<java.lang.String,java.lang.Object>
     **/
    @Override
    public RedisConnectionFactory buildConnectionFactory(int database) {
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisClusterConfiguration(), lettuceClientConfiguration());
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }

    /**
     * @author fangzhenxun
     * @description  redis集群模式的配置
     * @date  2020/1/8 18:56
     * @param
     * @return org.springframework.data.redis.connection.RedisStandaloneConfiguration
     **/
    public RedisClusterConfiguration redisClusterConfiguration() {
        RedisClusterConfiguration redisClusterConfiguration = new RedisClusterConfiguration(clusterRedisProperties.nodes());
        redisClusterConfiguration.setMaxRedirects(clusterRedisProperties.maxRedirects());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(clusterRedisProperties.password())) {
            redisClusterConfiguration.setPassword(clusterRedisProperties.password());
        }
        return redisClusterConfiguration;
    }

    /**
     * @author fangzhenxun
     * @description  连接池配置信息（如果不用连接池可以省略这一步）
     * 按照特定的格式可以自动读入配置文件内容到该配置信息中，prefix，表示从配置文件读取以前缀开头的信息，注入到GenericObjectPoolConfig中
     *      * 对象连接池的配置，注意该属性的顺序，genericObjectPoolConfig 在lettuceClientConfiguration的上面，因为有依赖关系，否则会出错
     * @date  2020/1/8 19:33
     * @param
     * @return org.apache.commons.pool2.impl.GenericObjectPoolConfig
     **/
    public GenericObjectPoolConfig genericObjectPoolConfig() {
        GenericObjectPoolConfig genericObjectPoolConfig = new GenericObjectPoolConfig();
        genericObjectPoolConfig.setMaxIdle(clusterRedisProperties.pollMaxIdle());
        genericObjectPoolConfig.setMinIdle(clusterRedisProperties.pollMinIdle());
        genericObjectPoolConfig.setMaxTotal(clusterRedisProperties.pollMaxActive());
        genericObjectPoolConfig.setMaxWaitMillis(clusterRedisProperties.pollMaxWait());
        return genericObjectPoolConfig;
    }

    /**
     * @author fangzhenxun
     * @description lettuce客户端配置信息连接池信息（如果不用连接池通过LettuceClientConfiguration来builder）
     * @date  2020/1/8 19:21
     * @return org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration
     **/
    public LettuceClientConfiguration lettuceClientConfiguration(){
        //构造LettucePoolingClientConfiguration对象连接池，同时加入连接池配置信息
        return LettucePoolingClientConfiguration
                .builder()
                .poolConfig(genericObjectPoolConfig())
                .commandTimeout(Duration.ofMillis(clusterRedisProperties.timeout()))
                .build();
    }

}
