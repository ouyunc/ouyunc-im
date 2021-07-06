package com.ouyu.cache.l1.distributed.redis.lettuce.config;


import com.ouyu.cache.l1.distributed.redis.lettuce.properties.LettuceClusterProperties;
import com.ouyu.cache.l1.distributed.redis.lettuce.properties.LettuceSentinelProperties;
import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 哨兵模式配置
 */
public class LettuceSentinelConfiguration extends LettuceConfiguration {

    private LettuceSentinelProperties lettuceSentinelProperties = ConfigFactory.create(LettuceSentinelProperties.class);


    public RedisURI sentinelRedisUri() {
        Set<String> nodes = lettuceSentinelProperties.nodes();
        RedisURI.Builder builder = RedisURI.builder()
                .withPassword(lettuceSentinelProperties.password().toCharArray())
                .withSentinelMasterId(lettuceSentinelProperties.masterId());
        for (String node : nodes) {
            String[] hostPort = node.split(":");
            builder.withSentinel(hostPort[0], Integer.parseInt(hostPort[1]));
        }
        return builder.build();
    }


    public RedisClient sentinelRedisClient() {
        return RedisClient.create(clientResources(), sentinelRedisUri());
    }


    /**
     * @Author fangzhenxun
     * @Description 连接信息
     * @return io.lettuce.core.api.StatefulRedisConnection<java.lang.String,java.lang.String>
     */
    public <K,V> StatefulRedisConnection<K,V> sentinelRedisConnection(RedisCodec<K,V> redisCodec) {
        return sentinelRedisClient().connect(redisCodec);
    }

    /**
     * @Author fangzhenxun
     * @Description 获取对象池
     * @param redisCodec
     * @return org.apache.commons.pool2.impl.GenericObjectPool<io.lettuce.core.api.StatefulRedisConnection<K,V>>
     */
    public <K,V> GenericObjectPool<StatefulRedisConnection<K ,V>> genericObjectPool(RedisCodec<K,V> redisCodec) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        return ConnectionPoolSupport.createGenericObjectPool(() -> sentinelRedisConnection(redisCodec), poolConfig);
    }
}