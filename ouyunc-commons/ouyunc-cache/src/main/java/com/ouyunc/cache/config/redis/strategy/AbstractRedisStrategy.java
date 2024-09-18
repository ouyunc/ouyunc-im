package com.ouyunc.cache.config.redis.strategy;

import com.ouyunc.cache.config.redis.properties.RedisProperties;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;
import org.redisson.config.Config;
import org.springframework.data.redis.connection.RedisConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceClientConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettucePoolingClientConfiguration;

/**
 * 抽象的redis 策略
 */
public abstract class AbstractRedisStrategy implements RedisStrategy {

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
     * @description  连接池配置信息（如果不用连接池可以省略这一步）
     * 按照特定的格式可以自动读入配置文件内容到该配置信息中，prefix，表示从配置文件读取以前缀开头的信息，注入到GenericObjectPoolConfig中
     *      * 对象连接池的配置，注意该属性的顺序，genericObjectPoolConfig 在lettuceClientConfiguration的上面，因为有依赖关系，否则会出错
     **/
    public GenericObjectPoolConfig<?> genericObjectPoolConfig() {
        GenericObjectPoolConfig<?> genericObjectPoolConfig = new GenericObjectPoolConfig<>();
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
     * @description 构建配置信息
     */
    @Override
    public Config buildRedissonConfig(int database, RedisProperties redisProperties) {
        this.redisProperties = redisProperties;
        return redissonConfiguration(database);
    }
}
