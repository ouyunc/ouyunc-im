package com.ouyu.im.config;

import com.ouyu.im.constant.enums.RouterStrategyEnum;
import io.netty.channel.ChannelOption;
import io.netty.util.NettyRuntime;
import org.aeonbits.owner.Config;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 使用建造者模式来进行构建对象实例
 * @Version V1.0
 **/
public class IMServerConfig extends IMConfig{


    /**
     * 默认server 端的绑定端口为6001
     */
    private int port;

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
     * 集群中服务间的重试次数默认3次
     */
    private int clusterServerRetry;

    /**
     *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认10秒
     */
    private int clusterServerInitRegisterPeriod;


    /**
     *  集群中，内置客户端读超时，单位秒，默认0秒钟
     */
    private int clusterServerIdleReadTimeOut;

    /**
     *  集群中，内置客户端写超时，单位秒，默认0秒钟
     */
    private int clusterServerIdleWriteTimeOut;

    /**
     *  集群中，内置客户端读写超时，单位秒，默认5秒钟
     */
    private int clusterServerIdleReadWriteTimeOut;

    /**
     * 集群中客户端channel pool 中核心连接数
     */
    private int clusterChannelPoolCoreConnection;

    /**
     * 集群中客户端channel pool 中最大连接数
     */
    private int clusterChannelPoolMaxConnection;

    /**
     * 是否开启ack，确保消息可靠，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
     */
    private boolean acknowledgeModeEnable;

    /**
     * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
     */
    private int heartBeatTimeout;


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


    public int getBossThreads() {
        return bossThreads;
    }


    public int getWorkThreads() {
        return workThreads;
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

    public int getHeartBeatTimeout() {
        return heartBeatTimeout;
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

    public int getClusterServerRetry() {
        return clusterServerRetry;
    }


    public int getClusterChannelPoolCoreConnection() {
        return clusterChannelPoolCoreConnection;
    }

    public int getClusterChannelPoolMaxConnection() {
        return clusterChannelPoolMaxConnection;
    }

    public int getClusterServerInitRegisterPeriod() {
        return clusterServerInitRegisterPeriod;
    }

    public int getClusterServerIdleReadTimeOut() {
        return clusterServerIdleReadTimeOut;
    }

    public int getClusterServerIdleWriteTimeOut() {
        return clusterServerIdleWriteTimeOut;
    }

    public int getClusterServerIdleReadWriteTimeOut() {
        return clusterServerIdleReadWriteTimeOut;
    }

    public boolean isAcknowledgeModeEnable() {
        return acknowledgeModeEnable;
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
         * 集群中服务间的重试次数默认3次
         */
        private int clusterServerRetry;

        /**
         *  集群中，服务启动时，服务注册表的增量更新时间，单位秒，默认5秒
         */
        private int clusterServerInitRegisterPeriod;



        /**
         *  集群中，内置客户端读超时，单位秒，默认0秒钟
         */
        private int clusterServerIdleReadTimeOut;

        /**
         *  集群中，内置客户端写超时，单位秒，默认0秒钟
         */
        private int clusterServerIdleWriteTimeOut;

        /**
         *  集群中，内置客户端读写超时，单位秒，默认5秒钟
         */
        private int clusterServerIdleReadWriteTimeOut;

        /**
         * 集群中客户端channel pool 中核心连接数
         */
        private int clusterChannelPoolCoreConnection;

        /**
         * 集群中客户端channel pool 中最大连接数
         */
        private int clusterChannelPoolMaxConnection;

        /**
         * 是否开启ack，确保消息可靠，开启会影响性能，建议权衡之后再决定是否开启, 默认不开启
         */
        private boolean acknowledgeModeEnable;

        /**
         * 单位秒 ， 外部客户端与服务端的心跳超时时间，如果服务端未收到客户端的心跳包在一定策略下会进行重试等待，最后如果如果没有连接上则将该客户端下线处理
         */
        private  int heartBeatTimeout;

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


        public Builder port(int port) {
            this.port = port;
            return this;
        }

        public Builder bossThreads(int bossThreads) {
            this.bossThreads = bossThreads;
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

        public Builder heartBeatTimeout(int heartBeatTimeout) {
            this.heartBeatTimeout = heartBeatTimeout;
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

        public Builder clusterServerRetry(int clusterServerRetry) {
            this.clusterServerRetry = clusterServerRetry;
            return this;
        }



        public Builder clusterChannelPoolCoreConnection(int clusterChannelPoolCoreConnection) {
            this.clusterChannelPoolCoreConnection = clusterChannelPoolCoreConnection;
            return this;
        }

        public Builder clusterChannelPoolMaxConnection(int clusterChannelPoolMaxConnection) {
            this.clusterChannelPoolMaxConnection = clusterChannelPoolMaxConnection;
            return this;
        }

        public Builder clusterServerInitRegisterPeriod(int clusterServerInitRegisterPeriod) {
            this.clusterServerInitRegisterPeriod = clusterServerInitRegisterPeriod;
            return this;
        }



        public Builder clusterServerIdleReadTimeOut(int clusterServerIdleReadTimeOut) {
            this.clusterServerIdleReadTimeOut = clusterServerIdleReadTimeOut;
            return this;
        }

        public Builder clusterServerIdleWriteTimeOut(int clusterServerIdleWriteTimeOut) {
            this.clusterServerIdleWriteTimeOut = clusterServerIdleWriteTimeOut;
            return this;
        }

        public Builder clusterServerIdleReadWriteTimeOut(int clusterServerIdleReadWriteTimeOut) {
            this.clusterServerIdleReadWriteTimeOut = clusterServerIdleReadWriteTimeOut;
            return this;
        }

        public Builder acknowledgeModeEnable(boolean acknowledgeModeEnable) {
            this.acknowledgeModeEnable = acknowledgeModeEnable;
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
            imServerConfig.bossThreads = this.bossThreads;
            imServerConfig.workThreads = this.workThreads;
            imServerConfig.clusterEnable = this.clusterEnable;
            imServerConfig.clusterAddress = this.clusterAddress;
            imServerConfig.clusterServerRetry = this.clusterServerRetry;
            imServerConfig.clusterServerInitRegisterPeriod = this.clusterServerInitRegisterPeriod;
            imServerConfig.clusterServerIdleReadTimeOut = this.clusterServerIdleReadTimeOut;
            imServerConfig.clusterServerIdleWriteTimeOut = this.clusterServerIdleWriteTimeOut;
            imServerConfig.clusterServerIdleReadWriteTimeOut = this.clusterServerIdleReadWriteTimeOut;
            imServerConfig.clusterChannelPoolCoreConnection = this.clusterChannelPoolCoreConnection;
            imServerConfig.clusterChannelPoolMaxConnection = this.clusterChannelPoolMaxConnection;
            imServerConfig.acknowledgeModeEnable = this.acknowledgeModeEnable;
            imServerConfig.heartBeatTimeout = this.heartBeatTimeout;
            imServerConfig.clusterServerRouteStrategy = this.clusterServerRouteStrategy;
            // 封装响应的map
            imServerConfig.channelOptionMap = this.channelOptionMap;
            imServerConfig.childChannelOptionMap = this.childChannelOptionMap;
            return imServerConfig;
        }
    }
}
