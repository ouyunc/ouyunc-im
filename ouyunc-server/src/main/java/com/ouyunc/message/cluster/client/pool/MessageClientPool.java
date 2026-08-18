package com.ouyunc.message.cluster.client.pool;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.utils.SocketAddressUtil;
import com.ouyunc.message.cluster.client.handler.MessageClientChannelPoolHandler;
import com.ouyunc.message.cluster.topology.ClusterTopologyView;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 这里使用客户端连接池来进行使用多个通道连接每个集群中的服务器端
 **/
public class MessageClientPool {
    private static final Logger log = LoggerFactory.getLogger(MessageClientPool.class);

    private static final String loopBackAddress = "127.0.0.1";

    private static final Bootstrap bootstrap;
    private static final EventLoopGroup workGroup;
    public static final ChannelPoolMap<String, SimpleChannelPool> clientSimpleChannelPoolMap = new AbstractChannelPoolMap<>() {
        @Override
        protected SimpleChannelPool newPool(String remoteHostPort) {
            //FixedChannelPool(Bootstrap bootstrap, 引导类
            //                            ChannelPoolHandler handler, handler 的创建类
            //                            ChannelHealthChecker healthCheck, 健康检查
            //                            AcquireTimeoutAction action,
            //                            final long acquireTimeoutMillis,等待连接池连接的最大时间，单位毫秒。
            //                            int maxConnections, 连接池中的最大连接数
            //                            int maxPendingAcquires, 在请求获取/建立连接大于maxConnections数时，创建等待建立连接的最大定时任务数量。例如maxConnections=2，此时已经建立了2连接，但是没有放入到连接池中，接下来的请求就会放入到一个后台执行的定时任务中，如果到了时间连接池中还没有连接，就可以建立不大于maxPendingAcquires的连接数，如果连接池中有连接了就从连接池中获取
            //                            boolean releaseHealthCheck, 释放检查
            //                            boolean lastRecentUsed) 获取连接的规则 FIFO/LIFO
            // 以下参数可以避免获取超时造成oom
            return new FixedChannelPool(bootstrap.remoteAddress(SocketAddressUtil.convert2SocketAddress(remoteHostPort)), new MessageClientChannelPoolHandler(), ChannelHealthChecker.ACTIVE, FixedChannelPool.AcquireTimeoutAction.NEW, MessageServerContext.serverProperties().getClusterClientChannelPoolAcquireTimeoutMillis(), MessageServerContext.serverProperties().getClusterClientChannelPoolMaxConnection(), MessageServerContext.serverProperties().getClusterClientChannelPoolMaxPendingAcquires(), true, false);
        }
    };

    // 初始化
    static {
        bootstrap = new Bootstrap();
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors()* NumberConstant.NUMBER_2);
        AttributeKey<String> clusterClientTagKey = AttributeKey.valueOf(MessageConstant.BOOTSTRAP_ATTR_KEY_TAG_CLIENT);;
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, MessageConstant.TRUE)
                .option(ChannelOption.SO_KEEPALIVE, MessageConstant.TRUE)
                .option(ChannelOption.TCP_NODELAY, MessageConstant.TRUE)
                .attr(clusterClientTagKey, MessageConstant.BOOTSTRAP_ATTR_KEY_TAG_CLUSTER_CLIENT_VALUE)
        ;

    }

    /**
     * @Author fzx
     * @Description 使用连接池初始化内置客户端，动态扩容缩容连接
     */
    public static void init(MessageServerProperties serverProperties) {
        log.info("IM内置客户端开始启动......");
        Set<String> nodes = serverProperties.getNodes();
        ClusterTopologyView topologyView = MessageServerContext.clusterTopologyView;
        for (String node : nodes) {
            String localServerAddress = loopBackAddress + MessageConstant.COLON + serverProperties.getPort();
            if (localServerAddress.equals(node) || serverProperties.getLocalServerAddress().equals(node)) {
                continue;
            }
            if (!topologyView.shouldConnect(node)) {
                log.info("分区策略跳过集群连接: {}", node);
                continue;
            }
            SimpleChannelPool simpleChannelPool = clientSimpleChannelPoolMap.get(node);
            MessageServerContext.clusterGlobalServerRegistryTableCache.put(node, simpleChannelPool);
            MessageServerContext.clusterActiveServerRegistryTableCache.put(node, simpleChannelPool);
        }
        log.info("IM内置客户端初始化完成");
    }


    /**
     * @Author fzx
     * @Description 注销内置客户端
     */
    public static void stop() {
        if (workGroup == null) {
            return;
        }
        try {
            if (!workGroup.shutdownGracefully().await(15, TimeUnit.SECONDS)) {
                log.warn("集群客户端 EventLoopGroup 在超时内未完全关闭");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("等待集群客户端 EventLoopGroup 关闭被中断");
            workGroup.shutdownGracefully();
        }
    }
}
