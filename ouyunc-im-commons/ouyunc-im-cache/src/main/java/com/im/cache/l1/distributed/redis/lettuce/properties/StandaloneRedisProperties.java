package com.im.cache.l1.distributed.redis.lettuce.properties;


import org.aeonbits.owner.Config;

/**
 * @author fangzhenxun
 * @date 2020/1/8 20:00
 * @description redis 单例模式的属性配置类
 * @ConfigurationProperties(prefix = "cache.redis.standalon") 来注入配置文件的值，或者使用@value() 方式来注入
 * @ConfigurationProperties 的 POJO类的命名比较严格,因为它必须和prefix的后缀名要一致, 不然值会绑定不上, 特殊的后缀名是“driver-class-name”这种带横杠的情况,在POJO里面的命名规则是 下划线转驼峰 就可以绑定成功，所以就是 “driverClassName”
 * 注意：这里使用属性RedisProperties.Lettuce 是为了接受从配置文件读取lettuce的pool连接池的配置属性，其实自己写也是可以的
 */
@Config.Sources({"classpath:ouyunc-im-server.properties","classpath:ouyunc-im-cache.properties"})
public interface StandaloneRedisProperties extends Config{

    /**
     * redis 连接数据库配置,默认0
     **/
    @Key("cache.redis.standalon.lettuce.database")
    @DefaultValue("0")
    int database();

    /**
     * redis 主机地址
     **/
    @Key("cache.redis.standalon.lettuce.host")
    @DefaultValue("127.0.0.1:6379")
    String host();

    /**
     * redis 主机端口号
     **/
    @Key("cache.redis.standalon.lettuce.port")
    @DefaultValue("6379")
    int port();

    /**
     * redis 主机密码
     **/
    @Key("cache.redis.standalon.lettuce.password")
    String password();

    /**
     * redis 连接超时时间
     **/
    @Key("cache.redis.standalon.lettuce.timeout")
    @DefaultValue("1000L")
    long timeout();

    /**
     * Maximum number of "idle" connections in the pool. Use a negative value to
     * indicate an unlimited number of idle connections.
     */
    @Key("cache.redis.standalon.lettuce.pool.max-idle")
    @DefaultValue("8")
    int pollMaxIdle();

    /**
     * Target for the minimum number of idle connections to maintain in the pool. This
     * setting only has an effect if both it and time between eviction runs are
     * positive.
     */
    @Key("cache.redis.standalon.lettuce.pool.min-idle")
    @DefaultValue("0")
    int pollMinIdle();

    /**
     * Maximum number of connections that can be allocated by the pool at a given
     * time. Use a negative value for no limit.
     */
    @Key("cache.redis.standalon.lettuce.pool.max-active")
    @DefaultValue("8")
    int pollMaxActive();

    /**
     * Maximum amount of time a connection allocation should block before throwing an
     * exception when the pool is exhausted. Use a negative value to block
     * indefinitely.
     */
    @Key("cache.redis.standalon.lettuce.pool.max-wait")
    @DefaultValue("-1")
    long pollMaxWait();

    /**
     * Time between runs of the idle object evictor thread. When positive, the idle
     * object evictor thread starts, otherwise no idle object eviction is performed.
     */
    @Key("cache.redis.standalon.lettuce.pool.time-between-eviction-runs")
    @DefaultValue("1")
    long pollTimeBetweenEvictionRuns();
}
