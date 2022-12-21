package com.im.cache.l1.distributed.redis.redisson.properties;

import org.aeonbits.owner.Config;


/**
 * @author fangzhenxun
 * @date 2020/1/9 13:09
 * @description redisson 哨兵模式的配置
 */
@Config.Sources({"classpath:ouyunc-im-cache.properties"})
public interface SentinelRedissonProperties extends Config{

    /**
     * redis 主节点的名称（在哨兵配置文件中配置的）
     **/
    @Key("cache.redis.sentinel.redisson.master")
    @DefaultValue("mymaster")
    String master();

    /**
     * redis 主机节点地址 + 端口号以英文逗号隔开
     **/
    @Key("cache.redis.sentinel.redisson.nodes")
    @DefaultValue("127.0.0.1:26379")
    String[] nodes();

    /**
     * redis 连接数据库配置,默认0
     **/
    @Key("cache.redis.sentinel.redisson.database")
    @DefaultValue("0")
    int database();

    /**
     * redis 密码
     **/
    @Key("cache.redis.sentinel.redisson.password")
    String password();

    /**
     * redis 连接超时时间
     **/
    @Key("cache.redis.sentinel.redisson.timeout")
    @DefaultValue("1000")
    int timeout();
    /**
     * redis 连接超时时间
     **/
    @Key("cache.redis.sentinel.redisson.conn-timeout")
    @DefaultValue("10000")
    int connTimeout();

    /**
     * 如果池连接未用于<code>超时</code>时间
     * 当前连接数大于最小空闲连接池大小，
     * 然后它将关闭并从池中移除
     * 以毫秒为单位的值
     **/
    @Key("cache.redis.sentinel.redisson.so-timeout")
    @DefaultValue("10000")
    int soTimeout();

    /**
     * redis 连接池大小默认
     **/
    @Key("cache.redis.sentinel.redisson.poll-size")
    @DefaultValue("50")
    int pollSize();

    /**
     * Maximum number of "idle" connections in the pool. Use a negative value to
     * indicate an unlimited number of idle connections.
     */
    @Key("cache.redis.sentinel.redisson.pool.max-idle")
    @DefaultValue("8")
    int pollMaxIdle();

    /**
     * Target for the minimum number of idle connections to maintain in the pool. This
     * setting only has an effect if both it and time between eviction runs are
     * positive.
     */
    @Key("cache.redis.sentinel.redisson.pool.min-idle")
    @DefaultValue("0")
    int pollMinIdle();

    /**
     * Maximum number of connections that can be allocated by the pool at a given
     * time. Use a negative value for no limit.
     */
    @Key("cache.redis.sentinel.redisson.pool.max-active")
    @DefaultValue("8")
    int pollMaxActive();

    /**
     * Maximum amount of time a connection allocation should block before throwing an
     * exception when the pool is exhausted. Use a negative value to block
     * indefinitely.
     */
    @Key("cache.redis.sentinel.redisson.pool.max-wait")
    @DefaultValue("-1")
    long pollMaxWait();

    /**
     * Time between runs of the idle object evictor thread. When positive, the idle
     * object evictor thread starts, otherwise no idle object eviction is performed.
     */
    @Key("cache.redis.sentinel.redisson.pool.time-between-eviction-runs")
    @DefaultValue("1")
    long pollTimeBetweenEvictionRuns();
}