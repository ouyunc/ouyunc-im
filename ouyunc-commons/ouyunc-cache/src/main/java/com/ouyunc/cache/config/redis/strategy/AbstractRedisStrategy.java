package com.ouyunc.cache.config.redis.strategy;

import com.google.common.collect.Lists;
import com.ouyunc.cache.config.redis.properties.RedisProperties;
import io.lettuce.core.api.StatefulConnection;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

import java.util.List;

/**
 * 抽象的redis 策略
 */
public abstract class AbstractRedisStrategy implements RedisStrategy {
    /**
     * 协议常量
     */
    private static final String REDIS_PREFIX = "redis://";
    private static final String REDISS_PREFIX = "rediss://";
    /**
     * redis单例模式的配置
     */
    public RedisProperties redisProperties;


    /**
     * 配置抽象redis config
     */
    public abstract RedisConfiguration redisConfiguration(int database);

    /**
     * 配置抽象redison config
     */
    public abstract Config redissonConfiguration(int database);

    /**
     * @author fzx
     * @description  单例redisTemplate 的策略实现类
     **/
    @Override
    public RedisConnectionFactory buildRedisConnectionFactory(int database, RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
        //组装LettuceConnectionFactory的构造方法
        LettuceConnectionFactory lettuceConnectionFactory = new LettuceConnectionFactory(redisConfiguration(database), lettuceClientConfiguration());
        //如果是手动注入，需要设置下面的afterPropertiesSet
        lettuceConnectionFactory.afterPropertiesSet();
        return lettuceConnectionFactory;
    }



    /**
     * @author fzx
     * @description 连接池配置（不用连接池可省略）。配置项来自 {@code spring.redis.lettuce.pool}。
     * 泛型须为 {@code GenericObjectPoolConfig<StatefulConnection<?, ?>>}，与 Lettuce 池化客户端一致。
     * 注意：{@code genericObjectPoolConfig} 须在 {@link #lettuceClientConfiguration()} 之前可用（依赖顺序）。
     */
    public GenericObjectPoolConfig<StatefulConnection<?, ?>> genericObjectPoolConfig() {
        GenericObjectPoolConfig<StatefulConnection<?, ?>> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
        RedisProperties.Lettuce lettuce = redisProperties.getLettuce();
        if (lettuce != null) {
            RedisProperties.Pool pool = lettuce.getPool();
            if (pool != null) {
                genericObjectPoolConfig.setMaxIdle(pool.getMaxIdle());
                genericObjectPoolConfig.setMinIdle(pool.getMinIdle());
                genericObjectPoolConfig.setMaxTotal(pool.getMaxActive());
                genericObjectPoolConfig.setMaxWait(pool.getMaxWait());
            }
        }
        return genericObjectPoolConfig;
    }

    /**
     * @author fzx
     * @description lettuce客户端配置信息连接池信息（如果不用连接池通过LettuceClientConfiguration来builder）
     **/
    public LettuceClientConfiguration lettuceClientConfiguration(){
        //构造LettucePoolingClientConfiguration对象连接池，同时加入连接池配置信息
        return LettucePoolingClientConfiguration
                .builder()
                .poolConfig(genericObjectPoolConfig())
                .commandTimeout(redisProperties.getTimeout())
                .build();
    }


    /***
     * @author fzx
     * @description 格式化节点信息
     */
    protected List<String> getFormattedNodes(List<String> rawNodes) {
        if (rawNodes == null || rawNodes.isEmpty()) {
            return null;
        }
        List<String> nodes = Lists.newArrayList();
        // 使用ssl
        if (redisProperties.getSsl() != null && redisProperties.getSsl().isEnabled()) {
            for (String rawNode : rawNodes) {
                nodes.add(REDISS_PREFIX + rawNode);
            }
        }else {
            // 未使用ssl
            for (String rawNode : rawNodes) {
                nodes.add(REDIS_PREFIX + rawNode);
            }
        }
        return nodes;
    }

    /***
     * @author fzx
     * @description 构建配置信息
     */
    @Override
    public Config buildRedissonConfig(int database, RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
        return redissonConfiguration(database);
    }
}
