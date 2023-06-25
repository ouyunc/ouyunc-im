package com.ouyunc.im.properties;

import com.ouyunc.im.constant.enums.RouterStrategyEnum;
import io.netty.handler.logging.LogLevel;
import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 读取properties 相关配置
 **/
@Config.Sources({"classpath:ouyunc-im-server.properties"})
public interface IMServerProperties extends Config {
    /**
     * 默认server 端的绑定端口为6001
     */
    @Key("im.server.port")
    @DefaultValue("6001")
    int port();


    /**
     * 日志级别,默认INFO; TRACE, DEBUG, INFO, WARN, ERROR
     */
    @Key("im.server.log.level")
    @DefaultValue("INFO")
    LogLevel logLevel();

    /**
     * boss 线程组个数,默认与netty保持一致
     */
    @Key("im.server.boss.threads")
    @DefaultValue("1")
    int bossThreads();


    /**
     * work 线程组个数，默认与netty保持一致
     */
    @Key("im.server.worker.threads")
    @DefaultValue("8")
    int workThreads();


    /**
     * 服务端是否启动集群，如果开启下面的ip + port 需要配置
     */
    @Key("im.server.cluster.enable")
    @DefaultValue("false")
    boolean clusterEnable();

    /**
     *  集群中的服务ip + port (包括自己本身的ip + port), 例如：有10 台服务做集群，就把十台的服务端的IP以及端口号写上即可
     */
    @Key("im.server.cluster.address")
    Set<String> clusterAddress();


    /**
     *  集群中的服务开启脑裂检测，默认开启
     */
    @Key("im.server.cluster.split-brain.detection.enable")
    @DefaultValue("true")
    boolean clusterSplitBrainDetectionEnable();

    /**
     * 单位分钟， 集群中，开始检测脑裂的延迟时间（服务启动后多久开始进行脑裂的检测），之后就每个心跳检测一次
     */
    @Key("im.server.cluster.split-brain.detection.delay")
    @DefaultValue("30")
    long clusterSplitBrainDetectionDelay();

    /**
     * 集群中消息重试次数，消息如果不通，会进行重试三次
     */
    @Key("im.server.cluster.message.retry")
    @DefaultValue("3")
    int clusterMessageRetry();


    /**
     *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认5秒
     */
    @Key("im.server.cluster.inner-client.heart-beat.interval")
    @DefaultValue("5")
    int clusterInnerClientHeartbeatInterval();


    /**
     *  集群中，内置客户端读超时，单位秒，默认0秒钟
     */
    @Key("im.server.cluster.inner-client.idle-read-time-out")
    @DefaultValue("0")
    int clusterInnerClientIdleReadTimeOut();

    /**
     *  集群中，内置客户端写超时，单位秒，默认0秒钟
     */
    @Key("im.server.cluster.inner-client.idle-write-time-out")
    @DefaultValue("0")
    int clusterInnerClientIdleWriteTimeOut();

    /**
     *  集群中，内置客户端读写超时，单位秒，默认5秒钟
     */
    @Key("im.server.cluster.inner-client.idle-read-write-time-out")
    @DefaultValue("5")
    int clusterInnerClientIdleReadWriteTimeOut();

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    @Key("im.server.cluster.inner-client.channel.pool.core-connection")
    @DefaultValue("5")
    int clusterInnerClientChannelPoolCoreConnection();

    /**
     * 集群中客户端channel pool 中最大连接数
     */
    @Key("im.server.cluster.inner-client.channel.pool.max-connection")
    @DefaultValue("50")
    int clusterInnerClientChannelPoolMaxConnection();

    /**
     * 集群中客户端channel pool 中，等待连接池连接的最大时间，单位毫秒, 默认10s
     */
    @Key("im.server.cluster.inner-client.channel.pool.acquire-timeout-millis")
    @DefaultValue("10000")
    long clusterInnerClientChannelPoolAcquireTimeoutMillis();

    /**
     * 集群中客户端channel pool 中，在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
     */
    @Key("im.server.cluster.inner-client.channel.pool.max-pending-acquires")
    @DefaultValue("100000")
    int clusterInnerClientChannelPoolMaxPendingAcquires();

    /**
     * 集群中内部客户端，发送syn最大等待3个心跳时间段，如果没有及时得到响应则进行本地下线
     */
    @Key("im.server.cluster.inner-client.heart-beat.wait-retry")
    @DefaultValue("3")
    int clusterInnerClientHeartbeatWaitRetry();

    /**
     *  集群中，路由服务的策略，默认回溯，RANDOM，BACKTRACK
     */
    @Key("im.server.cluster.route-strategy")
    @DefaultValue("RANDOM")
    RouterStrategyEnum clusterServerRouteStrategy();

    /**
     * 全局是否开启用户认证，主要校验用户的登录状态以及授权范围，默认开， true-开， false-关
     */
    @Key("im.server.auth.enable")
    @DefaultValue("true")
    boolean authEnable();

    /**
     * 是否开启消息数据库存储，默认true
     */
    @Key("im.server.db.enable")
    @DefaultValue("true")
    boolean dbEnable();

    /**
     * 是否开启好友在线状态的实施推送，默认false，只推送反向好友，不推送群（群成员状态可以按需拉取或定时拉取）
     */
    @Key("im.server.friend.online.push.enable")
    @DefaultValue("false")
    boolean friendOnlinePushEnable();

    /**
     * 是否开启ack，确保消息可靠，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
     */
    @Key("im.server.acknowledge-mode.enable")
    @DefaultValue("false")
    boolean acknowledgeModeEnable();

    /**
     * im 是否开启，已读回执(群聊和私聊)，需要客户端配合，默认否
     */
    @Key("im.server.read-receipt.enable")
    @DefaultValue("false")
    boolean readReceiptEnable();

    /**
     * im 是否开启登录校验，默认是
     */
    @Key("im.server.login-validate.enable")
    @DefaultValue("true")
    boolean loginValidateEnable();

    /**
     * im 是否开启登录最大连接数校验，开启登录校验后才会生效
     */
    @Key("im.server.login-max-connection-validate.enable")
    @DefaultValue("true")
    boolean loginMaxConnectionValidateEnable();

    /**
     * 全局是否开启客户端心跳，默认开启
     */
    @Key("im.server.heart-beat.enable")
    @DefaultValue("true")
    boolean heartBeatEnable();

    /**
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    @Key("im.server.heart-beat.timeout")
    @DefaultValue("5")
    int heartBeatTimeout();

    /**
     * 开启客户端后的心跳重试等待次数，默认3次，不能为负数
     */
    @Key("im.server.heart-beat.wait-retry")
    @DefaultValue("3")
    int heartBeatWaitRetry();

    /**
     *  全局是否开启SSL/TLS, 默认否
     */
    @Key("im.server.ssl.enable")
    @DefaultValue("false")
    boolean sslEnable();

    /**
     *  SSL/TLS 证书文件路径
     */
    @Key("im.server.ssl.certificate")
    @DefaultValue("ssl/ouyunc.com.pem")
    String sslCertificate();

    /**
     *  SSL/TLS 私钥文件路劲
     */
    @Key("im.server.ssl.private-key")
    @DefaultValue("ssl/ouyunc.com_pkcs8.key")
    String sslPrivateKey();





    /**
     * 指定了内核为此套接口排队的最大连接个数。对于给定的监听套接口，内核要维护两个队列:
     * 已连接队列：已完成连接队列三次握手已完成，内核正等待进程执行accept的调用中的数量
     * 未连接队列：未完成连接队列一个SYN已经到达，但三次握手还没有完成的连接中的数量
     */
    @Key("im.server.boss.option.so-backlog")
    @DefaultValue("1024")
    int bossOptionSoBacklog();

    /**
     * 地址复用，默认值False
     */
    @Key("im.server.boss.option.so-reuseaddr")
    @DefaultValue("true")
    boolean bossOptionSoReuseaddr();


    /**
     * 置连接活动保持连接状态
     */
    @Key("im.server.worker.child-option.so-keepalive")
    @DefaultValue("true")
    boolean workerChildOptionSoKeepalive();

    /**
     * 激活或者禁止TCP_NODELAY套接字选项，它决定了是否使用Nagle算法。如果是时延敏感型的应用，建议关闭Nagle算法。
     */
    @Key("im.server.worker.child-option.tcp-nodelay")
    @DefaultValue("true")
    boolean workerChildOptionTcpNoDelay();

    /**
     * 地址复用，默认值true
     */
    @Key("im.server.worker.child-option.so-reuseaddr")
    @DefaultValue("true")
    boolean workerChildOptionSoReuseaddr();
}
