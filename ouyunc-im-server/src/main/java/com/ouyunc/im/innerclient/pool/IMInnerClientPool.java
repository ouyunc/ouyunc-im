package com.ouyunc.im.innerclient.pool;

import com.ouyunc.im.config.IMServerConfig;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.innerclient.handler.IMInnerClientChannelPoolHandler;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Set;

/**
 * @Author fangzhenxun
 * @Description: 这里使用客户端连接池来进行使用多个通道连接每个集群中的服务器端
 **/
public class IMInnerClientPool {
    private static Logger log = LoggerFactory.getLogger(IMInnerClientPool.class);
    private static final Bootstrap bootstrap;
    private static final EventLoopGroup workGroup;
    public static final ChannelPoolMap<InetSocketAddress, SimpleChannelPool> singleClientChannelPoolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
        @Override
        protected SimpleChannelPool newPool(InetSocketAddress key) {
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
            return new FixedChannelPool(bootstrap.remoteAddress(key), new IMInnerClientChannelPoolHandler(), ChannelHealthChecker.ACTIVE, FixedChannelPool.AcquireTimeoutAction.NEW, IMServerContext.SERVER_CONFIG.getClusterInnerClientChannelPoolAcquireTimeoutMillis(), IMServerContext.SERVER_CONFIG.getClusterInnerClientChannelPoolMaxConnection(), IMServerContext.SERVER_CONFIG.getClusterInnerClientChannelPoolMaxPendingAcquires(), true, false);
        }
    };

    // 初始化
    static {
        bootstrap = new Bootstrap();
        workGroup = new NioEventLoopGroup();
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }

    /**
     * @Author fangzhenxun
     * @Description 使用连接池初始化内置客户端，动态扩容缩容连接
     * @param serverConfig
     * @return void
     */
    public static void init(IMServerConfig serverConfig){
        log.info("IM内置客户端开始启动......");
        // 获取集群中的所有服务地址列表
        Set<String> clusterAddress = serverConfig.getClusterAddress();
        for (String serverAddress : clusterAddress) {
            // 如果将本机的ip+port写在配置文件中，则排除本身
            // 获取本机ip与端口号
            String localServerAddress0 = IMConstant.LOCAL_HOST + IMConstant.COLON_SPLIT + serverConfig.getPort();
            // 排除本机,将集群服务存放到服务注册表中
            if  (!localServerAddress0.equals(serverAddress) && !IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(serverAddress)) {
                final InetSocketAddress inetSocketAddress = SocketAddressUtil.convert2SocketAddress(serverAddress);
                // 获取准备好的 channel pool，此时还没有连接
                SimpleChannelPool simpleChannelPool = singleClientChannelPoolMap.get(inetSocketAddress);
                // 默认一开始所有的连接都是存活的, 并且缓存所有连接
                IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.put(SocketAddressUtil.convert2HostPort(inetSocketAddress), simpleChannelPool);
                IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.put(SocketAddressUtil.convert2HostPort(inetSocketAddress), simpleChannelPool);
            }
        }
        log.info("IM内置客户端初始化完成");
    }

    /**
     * @Author fangzhenxun
     * @Description 注销内置客户端
     * @param
     * @return void
     */
    public static void stop(){
        if (workGroup != null) {
            workGroup.shutdownGracefully();
        }
    }
}
