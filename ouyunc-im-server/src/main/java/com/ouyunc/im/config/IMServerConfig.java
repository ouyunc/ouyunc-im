package com.ouyunc.im.config;

import com.ouyunc.im.constant.enums.RouterStrategyEnum;
import io.netty.channel.ChannelOption;
import io.netty.handler.logging.LogLevel;
import io.netty.util.NettyRuntime;
import org.aeonbits.owner.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 使用建造者模式来进行构建配置对象实例
 * @Version V3.0
 **/
public class IMServerConfig extends IMConfig{


    /**
     * 默认server 端的绑定端口为6001
     */
    private int port;

    /**
     * 日志级别,默认INFO; TRACE, DEBUG, INFO, WARN, ERROR
     */

    private LogLevel logLevel;

    /**
     * boss 线程组个数,默认与netty保持一致
     */
    private int bossThreads;


    /**
     * work 线程组个数，默认与netty保持一致
     */
    private int workThreads;


    /**
     * 服务端是否启动集群，如果开启下面的ip + port 需要配置
     */
    private boolean clusterEnable;

    /**
     * # 集群中的服务ip + port (包括自己本身的ip + port), 例如：有10 台服务做集群，就把十台的服务端的IP以及端口号写上即可
     */
    private Set<String> clusterAddress;


    /**
     *  集群中的服务开启脑裂检测，默认开启
     */
    private boolean clusterSplitBrainDetectionEnable;

    /**
     * 单位分钟， 集群中，开始检测脑裂的延迟时间（服务启动后多久开始进行脑裂的检测），之后就每个心跳检测一次
     */
    private long clusterSplitBrainDetectionDelay;


    /**
     * 集群中消息重试次数，消息如果不通，会进行重试三次
     */
    private int clusterMessageRetry;

    /**
     *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认10秒
     */
    private int clusterInnerClientHeartbeatInterval;


    /**
     *  集群中，内置客户端读超时，单位秒，默认0秒钟
     */
    private int clusterInnerClientIdleReadTimeOut;

    /**
     *  集群中，内置客户端写超时，单位秒，默认0秒钟
     */
    private int clusterInnerClientIdleWriteTimeOut;

    /**
     *  集群中，内置客户端读写超时，单位秒，默认5秒钟
     */
    private int clusterInnerClientIdleReadWriteTimeOut;

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    private int clusterInnerClientChannelPoolCoreConnection;

    /**
     * 集群中客户端channel pool 中最大连接数
     */
    private int clusterInnerClientChannelPoolMaxConnection;

    /**
     * 集群中客户端channel pool 中，等待连接池连接的最大时间，单位毫秒, 默认10s
     */
    private long clusterInnerClientChannelPoolAcquireTimeoutMillis;

    /**
     * 集群中客户端channel pool 中，在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
     */
    private int clusterInnerClientChannelPoolMaxPendingAcquires;

    /**
     * 集群中内部客户端，发送syn最大等待3个心跳时间段，如果没有及时得到响应则进行本地下线
     */
    private int clusterInnerClientHeartbeatWaitRetry;

    /**
     * 全局是否开启用户认证，主要校验用户的登录状态以及授权范围，默认开， true-开， false-关
     */
    private boolean authEnable;


    /**
     * 是否开启消息数据库存储，默认true
     */
    private boolean messageDbEnable;

    /**
     * 是否开启好友在线状态的实施推送，默认false，只推送反向好友，不推送群（群成员状态可以按需拉取或定时拉取）
     */
    private boolean friendOnlinePushEnable;

    /**
     * 是否开启ack，确保消息可靠qos，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
     */
    private boolean acknowledgeModeEnable;

    /**
     * im 是否开启，已读回执(群聊和私聊)，需要客户端配合，默认否
     */
    private boolean readReceiptEnable;


    /**
     * 全局是否开启客户端心跳，默认开启
     */
    private boolean heartBeatEnable;

    /**
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    private int heartBeatTimeout;

    /**
     * 开启客户端后的心跳重试等待次数，默认3次，不能为负数
     */
    private int heartBeatWaitRetry;

    /**
     * 集群中服务的路由策略
     */
    RouterStrategyEnum clusterServerRouteStrategy;


    /**
     * 通过builder来将配置文件中的参数组装到这个map 中, option
     */
    private Map<ChannelOption, Object> channelOptionMap;

    /**
     * 通过builder来将配置文件中的参数组装到这个map 中,childOption
     */
    private Map<ChannelOption, Object> childChannelOptionMap;


    public boolean isFriendOnlinePushEnable() {
        return friendOnlinePushEnable;
    }

    public LogLevel getLogLevel() {
        return logLevel;
    }

    public boolean isAuthEnable() {
        return authEnable;
    }

    public int getBossThreads() {
        return bossThreads;
    }

    public boolean isReadReceiptEnable() {
        return readReceiptEnable;
    }

    public int getWorkThreads() {
        return workThreads;
    }

    public long getClusterSplitBrainDetectionDelay() {
        return clusterSplitBrainDetectionDelay;
    }

    public int getClusterMessageRetry() {
        return clusterMessageRetry;
    }

    public int getPort() {
        return port;
    }

    public boolean isClusterEnable() {
        return clusterEnable;
    }

    public Set<String> getClusterAddress() {
        return clusterAddress;
    }

    public boolean isHeartBeatEnable() {
        return heartBeatEnable;
    }

    public boolean isMessageDbEnable() {
        return messageDbEnable;
    }

    public int getHeartBeatTimeout() {
        return heartBeatTimeout;
    }

    public int getHeartBeatWaitRetry() {
        return heartBeatWaitRetry;
    }

    public Map<ChannelOption, Object> getChannelOptionMap() {
        return channelOptionMap;
    }

    public Map<ChannelOption, Object> getChildChannelOptionMap() {
        return childChannelOptionMap;
    }

    public RouterStrategyEnum getClusterServerRouteStrategy() {
        return clusterServerRouteStrategy;
    }

    public boolean isClusterSplitBrainDetectionEnable() {
        return clusterSplitBrainDetectionEnable;
    }


    public int getClusterInnerClientHeartbeatInterval() {
        return clusterInnerClientHeartbeatInterval;
    }

    public int getClusterInnerClientIdleReadTimeOut() {
        return clusterInnerClientIdleReadTimeOut;
    }

    public int getClusterInnerClientIdleWriteTimeOut() {
        return clusterInnerClientIdleWriteTimeOut;
    }

    public int getClusterInnerClientIdleReadWriteTimeOut() {
        return clusterInnerClientIdleReadWriteTimeOut;
    }

    public int getClusterInnerClientChannelPoolCoreConnection() {
        return clusterInnerClientChannelPoolCoreConnection;
    }

    public int getClusterInnerClientChannelPoolMaxConnection() {
        return clusterInnerClientChannelPoolMaxConnection;
    }

    public long getClusterInnerClientChannelPoolAcquireTimeoutMillis() {
        return clusterInnerClientChannelPoolAcquireTimeoutMillis;
    }

    public int getClusterInnerClientChannelPoolMaxPendingAcquires() {
        return clusterInnerClientChannelPoolMaxPendingAcquires;
    }

    public boolean isAcknowledgeModeEnable() {
        return acknowledgeModeEnable;
    }

    public int getClusterInnerClientHeartbeatWaitRetry() {
        return clusterInnerClientHeartbeatWaitRetry;
    }

    /**
     * @Author fangzhenxun
     * @Description builder 入口
     * @return com.ouyu.im.config.IMServerConfig.Builder
     */
    public static Builder newBuilder(){
        return new Builder();
    }

    /**
     * 多属性赋值使用建造者模式 https://www.cnblogs.com/scuwangjun/p/9699895.html
     */
    public static class Builder {

        /**
         * 默认server 端的绑定端口为6001
         */
        private int port;

        /**
         * 日志级别,默认INFO; TRACE, DEBUG, INFO, WARN, ERROR
         */

        private LogLevel logLevel;

        /**
         * 本地host地址，通过InetAddress.getLocalHost().getHostAddress()获取
         */
        private String localHost;

        /**
         * 本地服务地址 ip:port
         */
        private String localServerAddress;


        /**
         * boss 线程组个数,默认与netty保持一致
         */
        private int bossThreads = NettyRuntime.availableProcessors() * 2;


        /**
         * work 线程组个数，默认与netty保持一致
         */
        private int workThreads = NettyRuntime.availableProcessors() * 2;


        /**
         * 服务端是否启动集群，如果开启下面的ip + port 需要配置
         */
        private  boolean clusterEnable;

        /**
         * # 集群中的服务ip + port (包括自己本身的ip + port), 例如：有10 台服务做集群，就把十台的服务端的IP以及端口号写上即可
         */
        private Set<String> clusterAddress = new HashSet<String>();


        /**
         *  集群中的服务开启脑裂检测，默认开启
         */
        private boolean clusterSplitBrainDetectionEnable;

        /**
         * 单位分钟， 集群中，开始检测脑裂的延迟时间（服务启动后多久开始进行脑裂的检测），之后就每个心跳检测一次
         */
        private long clusterSplitBrainDetectionDelay;


        /**
         * 集群中消息重试次数，消息如果不通，会进行重试三次
         */
        private int clusterMessageRetry;

        /**
         *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认5秒
         */
        private int clusterInnerClientHeartbeatInterval;



        /**
         *  集群中，内置客户端读超时，单位秒，默认0秒钟
         */
        private int clusterInnerClientIdleReadTimeOut;

        /**
         *  集群中，内置客户端写超时，单位秒，默认0秒钟
         */
        private int clusterInnerClientIdleWriteTimeOut;

        /**
         *  集群中，内置客户端读写超时，单位秒，默认5秒钟
         */
        private int clusterInnerClientIdleReadWriteTimeOut;

        /**
         * 集群中客户端channel pool 中核心连接数
         */
        private int clusterInnerClientChannelPoolCoreConnection;

        /**
         * 集群中客户端channel pool 中最大连接数
         */
        private int clusterInnerClientChannelPoolMaxConnection;

        /**
         * 集群中客户端channel pool 中，等待连接池连接的最大时间，单位毫秒, 默认10s
         */
        private long clusterInnerClientChannelPoolAcquireTimeoutMillis;

        /**
         * 集群中客户端channel pool 中，在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
         */
        private int clusterInnerClientChannelPoolMaxPendingAcquires;


        /**
         * 集群中内部客户端，发送syn最大等待3个心跳时间段，如果没有及时得到响应则进行本地下线
         */
        private int clusterInnerClientHeartbeatWaitRetry;

        /**
         * 全局是否开启用户认证，主要校验用户的登录状态以及授权范围，默认开， true-开， false-关
         */
        private boolean authEnable;


        /**
         * 是否开启消息数据库存储，默认true
         */
        private boolean messageDbEnable;

        /**
         * 是否开启好友在线状态的实施推送，默认false，只推送反向好友，不推送群（群成员状态可以按需拉取或定时拉取）
         */
        private boolean friendOnlinePushEnable;

        /**
         * 是否开启ack，确保消息可靠，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
         */
        private boolean acknowledgeModeEnable;

        /**
         * im 是否开启，已读回执(群聊和私聊)，需要客户端配合，默认否
         */
        private boolean readReceiptEnable;

        /**
         * 全局是否开启客户端心跳，默认开启
         */
        private boolean heartBeatEnable;

        /**
         * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
         */
        private  int heartBeatTimeout;


        /**
         * 开启客户端后的心跳重试等待次数，默认3次，不能为负数
         */
        private int heartBeatWaitRetry;

        /**
         *  全局是否开启SSL/TLS, 默认否
         */
        private boolean sslEnable;

        /**
         *  SSL/TLS 证书文件路径
         */

        private String sslCertificate;

        /**
         *  SSL/TLS 私钥文件路劲
         */
        private String sslPrivateKey;

        /**
         * 集群中服务的路由策略
         */
        private RouterStrategyEnum clusterServerRouteStrategy;


        /**
         * 指定了内核为此套接口排队的最大连接个数。对于给定的监听套接口，内核要维护两个队列:
         * 已连接队列：已完成连接队列三次握手已完成，内核正等待进程执行accept的调用中的数量
         * 未连接队列：未完成连接队列一个SYN已经到达，但三次握手还没有完成的连接中的数量
         */
        private  int bossOptionSoBacklog;

        /**
         * # 地址复用，默认值False
         */
        private  boolean bossOptionSoReuseaddr;

        /**
         * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
         */
        private  boolean workerChildOptionSoKeepalive;

        /**
         * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
         */
        private  boolean workerChildOptionTcpNoDelay;

        /**
         * # 地址复用，默认值False
         */
        private  boolean workerChildOptionSoReuseaddr;


        /**
         * 通过builder来将配置文件中的参数组装到这个map 中, option
         */
        private Map<ChannelOption, Object> channelOptionMap = new HashMap<>();

        /**
         * 通过builder来将配置文件中的参数组装到这个map 中,childOption
         */
        private Map<ChannelOption, Object> childChannelOptionMap = new HashMap<>();

        public Builder localHost(String localHost) {
            this.localHost = localHost;
            return this;
        }

        public Builder localServerAddress(String localServerAddress) {
            this.localServerAddress = localServerAddress;
            return this;
        }

        public Builder logLevel(LogLevel logLevel) {
            this.logLevel = logLevel;
            return this;
        }

        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder bossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
            return this;
        }

        public Builder messageDbEnable(boolean messageDbEnable) {
            this.messageDbEnable = messageDbEnable;
            return this;
        }

        public Builder workThreads(int workThreads) {
            this.workThreads = workThreads;
            return this;
        }

        public Builder clusterEnable(boolean clusterEnable) {
            this.clusterEnable = clusterEnable;
            return this;
        }

        public Builder clusterAddress(Set<String> clusterAddress) {
            this.clusterAddress = clusterAddress;
            return this;
        }

        public Builder clusterSplitBrainDetectionEnable(boolean clusterSplitBrainDetectionEnable) {
            this.clusterSplitBrainDetectionEnable = clusterSplitBrainDetectionEnable;
            return this;
        }

        public Builder clusterSplitBrainDetectionDelay(long clusterSplitBrainDetectionDelay) {
            this.clusterSplitBrainDetectionDelay = clusterSplitBrainDetectionDelay;
            return this;
        }

        public Builder clusterMessageRetry(int clusterMessageRetry) {
            this.clusterMessageRetry = clusterMessageRetry;
            return this;
        }

        public Builder clusterInnerClientHeartbeatWaitRetry(int clusterInnerClientHeartbeatWaitRetry) {
            this.clusterInnerClientHeartbeatWaitRetry = clusterInnerClientHeartbeatWaitRetry;
            return this;
        }

        public Builder heartBeatEnable(boolean heartBeatEnable) {
            this.heartBeatEnable = heartBeatEnable;
            return this;
        }

        public Builder heartBeatTimeout(int heartBeatTimeout) {
            this.heartBeatTimeout = heartBeatTimeout;
            return this;
        }

        public Builder heartBeatWaitRetry(int heartBeatWaitRetry) {
            this.heartBeatWaitRetry = heartBeatWaitRetry;
            return this;
        }

        public Builder bossOptionSoBacklog(int bossOptionSoBacklog) {
            this.bossOptionSoBacklog = bossOptionSoBacklog;
            this.channelOptionMap.put(ChannelOption.SO_BACKLOG, bossOptionSoBacklog);
            return this;
        }

        public Builder workerChildOptionSoKeepalive(boolean workerChildOptionSoKeepalive) {
            this.workerChildOptionSoKeepalive = workerChildOptionSoKeepalive;
            this.childChannelOptionMap.put(ChannelOption.SO_KEEPALIVE, workerChildOptionSoKeepalive);
            return this;
        }

        public Builder workerChildOptionTcpNoDelay(boolean workerChildOptionTcpNoDelay) {
            this.workerChildOptionTcpNoDelay = workerChildOptionTcpNoDelay;
            this.childChannelOptionMap.put(ChannelOption.TCP_NODELAY, workerChildOptionTcpNoDelay);
            return this;
        }

        public Builder bossOptionSoReuseaddr(boolean bossOptionSoReuseaddr) {
            this.bossOptionSoReuseaddr = bossOptionSoReuseaddr;
            this.channelOptionMap.put(ChannelOption.SO_REUSEADDR, bossOptionSoReuseaddr);
            return this;
        }

        public Builder workerChildOptionSoReuseaddr(boolean workerChildOptionSoReuseaddr) {
            this.workerChildOptionSoReuseaddr = workerChildOptionSoReuseaddr;
            this.childChannelOptionMap.put(ChannelOption.SO_REUSEADDR, workerChildOptionSoReuseaddr);
            return this;
        }

        public Builder clusterServerRouteStrategy(RouterStrategyEnum clusterServerRouteStrategy) {
            this.clusterServerRouteStrategy = clusterServerRouteStrategy;
            return this;
        }

        public Builder clusterInnerClientHeartbeatInterval(int clusterInnerClientHeartbeatInterval) {
            this.clusterInnerClientHeartbeatInterval = clusterInnerClientHeartbeatInterval;
            return this;
        }

        public Builder clusterInnerClientIdleReadTimeOut(int clusterInnerClientIdleReadTimeOut) {
            this.clusterInnerClientIdleReadTimeOut = clusterInnerClientIdleReadTimeOut;
            return this;
        }

        public Builder clusterInnerClientIdleWriteTimeOut(int clusterInnerClientIdleWriteTimeOut) {
            this.clusterInnerClientIdleWriteTimeOut = clusterInnerClientIdleWriteTimeOut;
            return this;
        }

        public Builder clusterInnerClientIdleReadWriteTimeOut(int clusterInnerClientIdleReadWriteTimeOut) {
            this.clusterInnerClientIdleReadWriteTimeOut = clusterInnerClientIdleReadWriteTimeOut;
            return this;
        }

        public Builder clusterInnerClientChannelPoolCoreConnection(int clusterInnerClientChannelPoolCoreConnection) {
            this.clusterInnerClientChannelPoolCoreConnection = clusterInnerClientChannelPoolCoreConnection;
            return this;
        }

        public Builder clusterInnerClientChannelPoolMaxConnection(int clusterInnerClientChannelPoolMaxConnection) {
            this.clusterInnerClientChannelPoolMaxConnection = clusterInnerClientChannelPoolMaxConnection;
            return this;
        }

        public Builder clusterInnerClientChannelPoolAcquireTimeoutMillis(long clusterInnerClientChannelPoolAcquireTimeoutMillis) {
            this.clusterInnerClientChannelPoolAcquireTimeoutMillis = clusterInnerClientChannelPoolAcquireTimeoutMillis;
            return this;
        }

        public Builder clusterInnerClientChannelPoolMaxPendingAcquires(int clusterInnerClientChannelPoolMaxPendingAcquires) {
            this.clusterInnerClientChannelPoolMaxPendingAcquires = clusterInnerClientChannelPoolMaxPendingAcquires;
            return this;
        }

        public Builder authEnable(boolean authEnable) {
            this.authEnable = authEnable;
            return this;
        }


        public Builder friendOnlinePushEnable(boolean friendOnlinePushEnable) {
            this.friendOnlinePushEnable = friendOnlinePushEnable;
            return this;
        }

        public Builder acknowledgeModeEnable(boolean acknowledgeModeEnable) {
            this.acknowledgeModeEnable = acknowledgeModeEnable;
            return this;
        }

        public Builder readReceiptEnable(boolean readReceiptEnable) {
            this.readReceiptEnable = readReceiptEnable;
            return this;
        }

        public Builder sslEnable(boolean sslEnable) {
            this.sslEnable = sslEnable;
            return this;
        }

        public Builder sslCertificate(String sslCertificate) {
            this.sslCertificate = sslCertificate;
            return this;
        }

        public Builder sslPrivateKey(String sslPrivateKey) {
            this.sslPrivateKey = sslPrivateKey;
            return this;

        }

        /**
         * @Author fangzhenxun
         * @Description 通过builder来组装返回数据
         * @param
         * @return com.ouyu.im.config.IMServerConfig
         */
        public IMServerConfig build() {
            IMServerConfig imServerConfig = new IMServerConfig();
            imServerConfig.port = this.port;
            imServerConfig.logLevel = this.logLevel;
            imServerConfig.localHost = this.localHost;
            imServerConfig.localServerAddress = this.localServerAddress;
            imServerConfig.bossThreads = this.bossThreads;
            imServerConfig.workThreads = this.workThreads;
            imServerConfig.clusterEnable = this.clusterEnable;
            imServerConfig.clusterAddress = this.clusterAddress;
            imServerConfig.clusterSplitBrainDetectionEnable = this.clusterSplitBrainDetectionEnable;
            imServerConfig.clusterSplitBrainDetectionDelay = this.clusterSplitBrainDetectionDelay;
            imServerConfig.clusterMessageRetry = this.clusterMessageRetry;

            imServerConfig.clusterInnerClientHeartbeatInterval = this.clusterInnerClientHeartbeatInterval;
            imServerConfig.clusterInnerClientIdleReadTimeOut = this.clusterInnerClientIdleReadTimeOut;
            imServerConfig.clusterInnerClientIdleWriteTimeOut = this.clusterInnerClientIdleWriteTimeOut;
            imServerConfig.clusterInnerClientIdleReadWriteTimeOut = this.clusterInnerClientIdleReadWriteTimeOut;
            imServerConfig.clusterInnerClientChannelPoolCoreConnection = this.clusterInnerClientChannelPoolCoreConnection;
            imServerConfig.clusterInnerClientChannelPoolMaxConnection = this.clusterInnerClientChannelPoolMaxConnection;
            imServerConfig.clusterInnerClientChannelPoolAcquireTimeoutMillis = this.clusterInnerClientChannelPoolAcquireTimeoutMillis;
            imServerConfig.clusterInnerClientChannelPoolMaxPendingAcquires = this.clusterInnerClientChannelPoolMaxPendingAcquires;
            imServerConfig.clusterInnerClientHeartbeatWaitRetry = this.clusterInnerClientHeartbeatWaitRetry;

            imServerConfig.authEnable = this.authEnable;
            imServerConfig.messageDbEnable = this.messageDbEnable;
            imServerConfig.friendOnlinePushEnable = this.friendOnlinePushEnable;
            imServerConfig.acknowledgeModeEnable = this.acknowledgeModeEnable;
            imServerConfig.readReceiptEnable = this.readReceiptEnable;
            imServerConfig.heartBeatEnable = this.heartBeatEnable;
            imServerConfig.heartBeatTimeout = this.heartBeatTimeout;
            imServerConfig.heartBeatWaitRetry = this.heartBeatWaitRetry;
            imServerConfig.sslEnable = this.sslEnable;
            imServerConfig.sslCertificate = this.sslCertificate;
            imServerConfig.sslPrivateKey = this.sslPrivateKey;
            imServerConfig.clusterServerRouteStrategy = this.clusterServerRouteStrategy;
            // 封装响应的map
            imServerConfig.channelOptionMap = this.channelOptionMap;
            imServerConfig.childChannelOptionMap = this.childChannelOptionMap;
            return imServerConfig;
        }
    }

    @Override
    public String toString() {
        return  "\n{" +
                "\n,  port=" + port +
                "\n, bossThreads=" + bossThreads +
                "\n, workThreads=" + workThreads +
                "\n, clusterEnable=" + clusterEnable +
                "\n, clusterAddress=" + clusterAddress +
                "\n, clusterSplitBrainDetectionEnable=" + clusterSplitBrainDetectionEnable +
                "\n, clusterSplitBrainDetectionDelay=" + clusterSplitBrainDetectionDelay +
                "\n, clusterMessageRetry=" + clusterMessageRetry +
                "\n, clusterInnerClientHeartbeatInterval=" + clusterInnerClientHeartbeatInterval +
                "\n, clusterInnerClientIdleReadTimeOut=" + clusterInnerClientIdleReadTimeOut +
                "\n, clusterInnerClientIdleWriteTimeOut=" + clusterInnerClientIdleWriteTimeOut +
                "\n, clusterInnerClientIdleReadWriteTimeOut=" + clusterInnerClientIdleReadWriteTimeOut +
                "\n, clusterInnerClientChannelPoolCoreConnection=" + clusterInnerClientChannelPoolCoreConnection +
                "\n, clusterInnerClientChannelPoolMaxConnection=" + clusterInnerClientChannelPoolMaxConnection +
                "\n, clusterInnerClientChannelPoolAcquireTimeoutMillis=" + clusterInnerClientChannelPoolAcquireTimeoutMillis +
                "\n, clusterInnerClientChannelPoolMaxPendingAcquires=" + clusterInnerClientChannelPoolMaxPendingAcquires +
                "\n, clusterInnerClientHeartbeatWaitRetry=" + clusterInnerClientHeartbeatWaitRetry +
                "\n, authEnable=" + authEnable +
                "\n, messageDbEnable=" + messageDbEnable +
                "\n, friendOnlinePushEnable=" + friendOnlinePushEnable +
                "\n, acknowledgeModeEnable=" + acknowledgeModeEnable +
                "\n, readReceiptEnable=" + readReceiptEnable +
                "\n, heartBeatEnable=" + heartBeatEnable +
                "\n, heartBeatTimeout=" + heartBeatTimeout +
                "\n, heartBeatWaitRetry=" + heartBeatWaitRetry +
                "\n, clusterServerRouteStrategy=" + clusterServerRouteStrategy +
                "\n, channelOptionMap=" + channelOptionMap +
                "\n, childChannelOptionMap=" + childChannelOptionMap +
                '}';
    }
}
