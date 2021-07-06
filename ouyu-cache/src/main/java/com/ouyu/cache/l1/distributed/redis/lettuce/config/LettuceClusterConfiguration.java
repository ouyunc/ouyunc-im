package com.ouyu.cache.l1.distributed.redis.lettuce.config;


import com.ouyu.cache.l1.distributed.redis.lettuce.properties.LettuceClusterProperties;
import com.ouyu.cache.l1.distributed.redis.redisson.properties.RedissonClusterProperties;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.cluster.RedisClusterClient;
import io.lettuce.core.cluster.api.StatefulRedisClusterConnection;
import io.lettuce.core.codec.RedisCodec;
import io.lettuce.core.support.ConnectionPoolSupport;
import org.aeonbits.owner.ConfigFactory;
import org.apache.commons.pool2.impl.GenericObjectPool;
import org.apache.commons.pool2.impl.GenericObjectPoolConfig;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description 哨兵模式配置
 */
public class LettuceClusterConfiguration extends LettuceConfiguration {

    private LettuceClusterProperties lettuceClusterProperties = ConfigFactory.create(LettuceClusterProperties.class);


    public List<RedisURI> clusterRedisUris() {
        List<RedisURI> redisURIList = new ArrayList<>();
        Set<String> nodes = lettuceClusterProperties.nodes();
        for (String node : nodes) {
            String[] hostPort = node.split(":");
            redisURIList.add(RedisURI.builder()
                    .withHost(hostPort[0])
                    .withPort(Integer.parseInt(hostPort[1]))
                    .withPassword(lettuceClusterProperties.password().toCharArray())
                    .build());
        }
        return redisURIList;
    }


    public RedisClusterClient redisClusterClient() {
        return RedisClusterClient.create(clientResources(), clusterRedisUris());
    }


    /**
     * @Author fangzhenxun
     * @Description 连接信息
     * @param
     * @return io.lettuce.core.api.StatefulRedisConnection<java.lang.String,java.lang.String>
     */
    public <K,V> StatefulRedisClusterConnection <K,V> clusterConnection(RedisCodec<K,V> redisCodec) {
        return redisClusterClient().connect(redisCodec);
    }


    /**
     * @Author fangzhenxun
     * @Description 获取对象池
     * @param redisCodec
     * @return org.apache.commons.pool2.impl.GenericObjectPool<io.lettuce.core.api.StatefulRedisConnection<K,V>>
     */
    public <K,V> GenericObjectPool<StatefulRedisConnection<K ,V>> genericObjectPool(RedisCodec<K,V> redisCodec) {
        GenericObjectPoolConfig poolConfig = new GenericObjectPoolConfig();
        return ConnectionPoolSupport.createGenericObjectPool(() -> clusterConnection(redisCodec), poolConfig);
    }
}