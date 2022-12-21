package com.ouyunc.im.db.properties;

import org.aeonbits.owner.Config;

@Config.Sources({"classpath:ouyunc-im-server.properties","classpath:ouyunc-im-db.properties"})
public interface DbProperties extends Config{

    /**
     * 数据库驱动名称
     **/
    @Key("db.driver-class-name")
    @DefaultValue("com.mysql.cj.jdbc.Driver")
    String driverClassName();

    /**
     * 数据库连接地址
     **/
    @Key("db.jdbc-url")
    @DefaultValue("jdbc:mysql://localhost:3306/db")
    String jdbcUrl();

    /**
     * 数据库用户名
     **/
    @Key("db.username")
    @DefaultValue("root")
    String username();

    /**
     * 数据库密码
     **/
    @Key("db.password")
    @DefaultValue("root")
    String password();

    /**
     * 从连接池中获取的连接是否是只读，默认false
     **/
    @Key("db.read-only")
    @DefaultValue("false")
    boolean readOnly();

    /**
     * 单位毫秒，此属性控制客户端（即您）等待来自池的连接的最大毫秒数。如果超过此时间而没有可用的连接，则会抛出SQLException。可接受的最低连接超时为250 ms。默认值：30000（30秒）
     **/
    @Key("db.connection-timeout")
    @DefaultValue("30000")
    int connectionTimeout();

    /**
     * 单位毫秒，此属性控制允许连接在池中保持空闲状态的最长时间。仅当minimumIdle定义为小于maximumPoolSize时，此设置才适用。一旦池达到MinimumIdle连接，空闲连接将不被取消。连接是否以空闲状态退役，最大变化为+30秒，平均变化为+15秒。在此超时之前，连接永远不会因为空闲而退役。值为0表示永远不会从池中删除空闲连接。最小允许值为10000ms（10秒）。默认值：600000（10分钟）
     **/
    @Key("db.idle-timeout")
    @DefaultValue("60000")
    int idleTimeout();

    /**
     # 单位毫秒，此属性控制池中连接的最大生存期。使用中的连接永远不会退出，只有在关闭连接后才将其删除。在逐个连接的基础上，应用较小的负衰减以避免池中的质量消灭。我们强烈建议设置此值，它应该比任何数据库或基础结构施加的连接时间限制短几秒钟。值0表示没有最大生存期（无限生存期），当然要遵守idleTimeout设置。最小允许值为30000ms（30秒）。默认值：1800000（30分钟）
     **/
    @Key("db.max-life-time")
    @DefaultValue("60000")
    int maxLifeTime();

    /**
     # 单位毫秒，此属性控制池中连接的最大生存期。使用中的连接永远不会退出，只有在关闭连接后才将其删除。在逐个连接的基础上，应用较小的负衰减以避免池中的质量消灭。我们强烈建议设置此值，它应该比任何数据库或基础结构施加的连接时间限制短几秒钟。值0表示没有最大生存期（无限生存期），当然要遵守idleTimeout设置。最小允许值为30000ms（30秒）。默认值：1800000（30分钟）
     **/
    @Key("db.minimum-idle")
    @DefaultValue("10")
    int minimumIdle();

    /**
     # 此属性控制允许池达到的最大大小，包括空闲和使用中的连接。基本上，此值将确定到数据库后端的最大实际连接数。合理的值最好由您的执行环境确定。当池达到此大小，并且没有空闲连接可用时，在超时之前，对getConnection（）的调用将最多阻塞connectionTimeout毫秒。请阅读有关池大小的信息。默认值：10
     **/
    @Key("db.maximum-pool-size")
    @DefaultValue("10")
    int maximumPoolSize();

    /**
     # 该属性表示连接池的用户定义名称，主要出现在日志记录和JMX管理控制台中，以识别池和池配置。默认值：自动生成
     **/
    @Key("db.pool-name")
    @DefaultValue("ouyunc")
    String poolName();


}
