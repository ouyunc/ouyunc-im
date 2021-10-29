package com.ouyu.im.config;

import com.ouyu.im.constant.enums.RouterStrategyEnum;
import org.aeonbits.owner.Config;

import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 读取properties 相关配置
 * @Version V1.0
 **/
@Config.Sources({"classpath:ouyu-im.properties"})
public interface PropertiesConfig extends Config {

    /**
     * 默认server 端的绑定端口为6001
     */
    @Key("im.server.port")
    @DefaultValue("6001")
    int port();

    /**
     * boss 线程组个数,默认与netty保持一致
     */
    @Key("im.server.boss.threads")
    int bossThreads();


    /**
     * work 线程组个数，默认与netty保持一致
     */
    @Key("im.server.worker.threads")
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
     * 集群中服务间的重试次数默认3次
     */
    @Key("im.server.cluster.server.retry")
    @DefaultValue("3")
    int clusterServerRetry();


    /**
     *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认5秒
     */
    @Key("im.server.cluster.server.init-register-period")
    @DefaultValue("5")
    int clusterServerInitRegisterPeriod();


    /**
     *  集群中，内置客户端读超时，单位秒，默认0秒钟
     */
    @Key("im.server.cluster.server.idle-read-time-out")
    @DefaultValue("0")
    int clusterServerIdleReadTimeOut();

    /**
     *  集群中，内置客户端写超时，单位秒，默认0秒钟
     */
    @Key("im.server.cluster.server.idle-write-time-out")
    @DefaultValue("0")
    int clusterServerIdleWriteTimeOut();

    /**
     *  集群中，内置客户端读写超时，单位秒，默认5秒钟
     */
    @Key("im.server.cluster.server.idle-read-write-time-out")
    @DefaultValue("5")
    int clusterServerIdleReadWriteTimeOut();

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    @Key("im.server.cluster.channel.pool.core-connection")
    @DefaultValue("5")
    int clusterChannelPoolCoreConnection();

    /**
     * 集群中客户端channel pool 中最大连接数
     */
    @Key("im.server.cluster.channel.pool.max-connection")
    @DefaultValue("50")
    int clusterChannelPoolMaxConnection();

    /**
     * 是否开启ack，确保消息可靠，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
     */
    @Key("im.server.acknowledge-mode.enable")
    @DefaultValue("false")
    boolean acknowledgeModeEnable();



    /**
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    @Key("im.server.heart-beat.timeout")
    @DefaultValue("5")
    int heartBeatTimeout();

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
     *  集群中当前全部的服务存储方式 默认内存存储
     */
    @Key("im.server.cluster.server.route-strategy")
    @DefaultValue("RANDOM")
    RouterStrategyEnum clusterServerRouteStrategy();



    /**
     * 指定了内核为此套接口排队的最大连接个数。对于给定的监听套接口，内核要维护两个队列:
     * 已连接队列：已完成连接队列三次握手已完成，内核正等待进程执行accept的调用中的数量
     * 未连接队列：未完成连接队列一个SYN已经到达，但三次握手还没有完成的连接中的数量
     */
    @Key("im.server.boss.option.SO_BACKLOG")
    @DefaultValue("1024")
    int bossOptionSoBacklog();

    /**
     * 地址复用，默认值False
     */
    @Key("im.server.boss.option.SO_REUSEADDR")
    @DefaultValue("true")
    boolean bossOptionSoReuseaddr();


    /**
     * 置连接活动保持连接状态
     */
    @Key("im.server.worker.child-option.SO_KEEPALIVE")
    @DefaultValue("true")
    boolean workerChildOptionSoKeepalive();

    /**
     * 激活或者禁止TCP_NODELAY套接字选项，它决定了是否使用Nagle算法。如果是时延敏感型的应用，建议关闭Nagle算法。
     */
    @Key("im.server.worker.child-option.TCP_NODELAY")
    @DefaultValue("true")
    boolean workerChildOptionTcpNoDelay();

    /**
     * 地址复用，默认值true
     */
    @Key("im.server.worker.child-option.SO_REUSEADDR")
    @DefaultValue("true")
    boolean workerChildOptionSoReuseaddr();

}
