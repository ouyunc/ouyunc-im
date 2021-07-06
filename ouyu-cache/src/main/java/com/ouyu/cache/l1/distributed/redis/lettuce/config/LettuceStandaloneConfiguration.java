package com.ouyu.cache.l1.distributed.redis.lettuce.config;

import cn.hutool.core.util.StrUtil;
import com.ouyu.cache.l1.distributed.redis.lettuce.properties.LettuceSentinelProperties;
import com.ouyu.cache.l1.distributed.redis.lettuce.properties.LettuceStandaloneProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

/**
 * @Author fangzhenxun
 * @Description: 单例配置
 * @Version V1.0
 **/
public class LettuceStandaloneConfiguration extends LettuceConfiguration {

    private LettuceStandaloneProperties lettuceStandaloneProperties = ConfigFactory.create(LettuceStandaloneProperties.class);



    /**
     * @Author fangzhenxun
     * @Description 构造RedisURI
     * @return io.lettuce.core.RedisURI
     */
    public RedisURI standaloneRedisUri() {
        String node = lettuceStandaloneProperties.nodes();
        String[] hostPort = node.split(":");

        String password = lettuceStandaloneProperties.password();
        RedisURI.Builder builder = RedisURI.builder()
                .withHost(hostPort[0])
                .withPort(Integer.parseInt(hostPort[1]))
                .withDatabase(lettuceStandaloneProperties.database());
        if (StrUtil.isNotBlank(password)) {
            builder.withPassword(password.toCharArray());

        }
         return builder.build();
    }


    /**
     * @Author fangzhenxun
     * @Description redis 客户端
     * @return io.lettuce.core.RedisClient
     */
    public RedisClient standaloneRedisClient() {
        return RedisClient.create(clientResources(), standaloneRedisUri());
    }


    /**
     * @Author fangzhenxun
     * @Description 连接信息
     * @param
     * @return io.lettuce.core.api.StatefulRedisConnection<java.lang.String,java.lang.String>
     */
    public <K,V> StatefulRedisConnection<K,V> standaloneRedisConnection(RedisCodec<K,V> redisCodec) {
        return standaloneRedisClient().connect(redisCodec);
    }

    /**
     * @Author fangzhenxun
     * @Description 获取对象池
     * @param redisCodec
     * @return org.apache.commons.pool2.impl.GenericObjectPool<io.lettuce.core.api.StatefulRedisConnection<K,V>>
     */
    public <K,V> GenericObjectPool<StatefulRedisConnection<K ,V>> genericObjectPool(RedisCodec<K,V> redisCodec) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        return ConnectionPoolSupport.createGenericObjectPool(() -> standaloneRedisConnection(redisCodec), poolConfig);
    }

}
