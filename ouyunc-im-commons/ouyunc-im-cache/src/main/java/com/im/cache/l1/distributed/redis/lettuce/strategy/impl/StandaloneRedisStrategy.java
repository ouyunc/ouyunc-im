package com.im.cache.l1.distributed.redis.lettuce.strategy.impl;


import com.im.cache.l1.distributed.redis.lettuce.enums.RedisEnum;
import com.im.cache.l1.distributed.redis.lettuce.properties.StandaloneRedisProperties;
import com.im.cache.l1.distributed.redis.lettuce.strategy.RedisStrategy;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisPassword;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;
import org.springframework.util.StringUtils;

import java.time.Duration;

/**
 * @author fangzhenxun
 * @date 2020/1/8 15:09
 * @description 单例redis配置,作为默认策略(至少有一个生效)
 */
public class StandaloneRedisStrategy implements RedisStrategy {

    /**
     * redis单例模式的配置
     */
    private static final StandaloneRedisProperties standaloneRedisProperties;

    static {
        standaloneRedisProperties = ConfigFactory.create(StandaloneRedisProperties.class);
    }

    /**
     * @author fangzhenxun
     * @description  单例模式类型
     * @date  2020/1/8 15:41
     * @param
     * @return com.xyt.cache.config.redis.lettuce.enums.RedisEnum
     **/
    @Override
    public RedisEnum getType() {
        return RedisEnum.STANDALONE;
    }


    /**
     * @author fangzhenxun
     * @description  单例redisTemplate 的策略实现类
     * @date  2020/1/8 15:10
     * @param
     * @return org.springframework.data.redis.core.RedisTemplate<java.lang.String,java.lang.Object>
     **/
    @Override
    public RedisConnectionFactory buildConnectionFactory(int database) {
        //组装LettuceConnectionFactory的构造方法
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(standaloneConfiguration(database), lettuceClientConfiguration());
        //如果是手动注入，需要设置下面的afterPropertiesSet
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }


    /**
     * @author fangzhenxun
     * @description  redis单例的配置
     * @date  2020/1/8 18:56
     * @param
     * @return org.springframework.data.redis.connection.RedisStandaloneConfiguration
     **/
    public RedisStandaloneConfiguration standaloneConfiguration(int database) {
        RedisStandaloneConfiguration redisStandaloneConfiguration = new RedisStandaloneConfiguration();
        redisStandaloneConfiguration.setPort(standaloneRedisProperties.port());
        redisStandaloneConfiguration.setDatabase(standaloneRedisProperties.database());
        redisStandaloneConfiguration.setHostName(standaloneRedisProperties.host());
        //如果密码不为空则设置密码
        if (StringUtils.hasLength(standaloneRedisProperties.password())) {
            redisStandaloneConfiguration.setPassword(RedisPassword.of(standaloneRedisProperties.password()));
        }
        if (0 <= database && database <= 16) {
            redisStandaloneConfiguration.setDatabase(database);
        }
        return redisStandaloneConfiguration;
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
        genericObjectPoolConfig.setMaxIdle(standaloneRedisProperties.pollMaxIdle());
        genericObjectPoolConfig.setMinIdle(standaloneRedisProperties.pollMinIdle());
        genericObjectPoolConfig.setMaxTotal(standaloneRedisProperties.pollMaxActive());
        genericObjectPoolConfig.setMaxWaitMillis(standaloneRedisProperties.pollMaxWait());
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
                .commandTimeout(Duration.ofMillis(standaloneRedisProperties.timeout()))
                .build();
    }

}
