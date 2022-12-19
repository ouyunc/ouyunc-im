package com.im.cache.l1.distributed.redis.lettuce.properties;

import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @author fangzhenxun
 * @date 2020/1/9 13:09
 * @description redis 集群模式的配置
 */
@Config.Sources({"classpath:ouyunc-im-cache.properties"})
public interface ClusterRedisProperties extends Config {

    /**
     * redis 主机节点地址 + 端口号以英文逗号隔开
     **/
    @Key("cache.redis.cluster.lettuce.nodes")
    @DefaultValue("127.0.0.1:6379")
    Set<String> nodes();

    /**
     * redis 集群模式下，集群最大转发的数量
     **/
    @Key("cache.redis.cluster.lettuce.max-redirects")
    @DefaultValue("3")
    int maxRedirects();


    /**
     * redis 密码
     **/
    @Key("cache.redis.cluster.lettuce.password")
    String password();

    /**
     * redis 连接超时时间
     **/
    @Key("cache.redis.cluster.lettuce.timeout")
    @DefaultValue("1000L")
    long timeout();


    /**
     * Maximum number of "idle" connections in the pool. Use a negative value to
     * indicate an unlimited number of idle connections.
     */
    @Key("cache.redis.cluster.lettuce.pool.max-idle")
    @DefaultValue("8")
    int pollMaxIdle();

    /**
     * Target for the minimum number of idle connections to maintain in the pool. This
     * setting only has an effect if both it and time between eviction runs are
     * positive.
     */
    @Key("cache.redis.cluster.lettuce.pool.min-idle")
    @DefaultValue("0")
    int pollMinIdle();

    /**
     * Maximum number of connections that can be allocated by the pool at a given
     * time. Use a negative value for no limit.
     */
    @Key("cache.redis.cluster.lettuce.pool.max-active")
    @DefaultValue("8")
    int pollMaxActive();

    /**
     * Maximum amount of time a connection allocation should block before throwing an
     * exception when the pool is exhausted. Use a negative value to block
     * indefinitely.
     */
    @Key("cache.redis.cluster.lettuce.pool.max-wait")
    @DefaultValue("-1")
    long pollMaxWait();

    /**
     * Time between runs of the idle object evictor thread. When positive, the idle
     * object evictor thread starts, otherwise no idle object eviction is performed.
     */
    @Key("cache.redis.cluster.lettuce.pool.time-between-eviction-runs")
    @DefaultValue("1")
    long pollTimeBetweenEvictionRuns();


}
