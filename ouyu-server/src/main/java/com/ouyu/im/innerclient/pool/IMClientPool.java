package com.ouyu.im.innerclient.pool;

import com.ouyu.im.config.IMServerConfig;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.innerclient.handler.IMClientChannelPoolHandler;
import com.ouyu.im.utils.SocketAddressUtil;
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
 * @Version V1.0
 **/
public class IMClientPool {
    private static Logger log = LoggerFactory.getLogger(IMClientPool.class);
    private static final Bootstrap bootstrap;
    private static final EventLoopGroup workGroup;
    public static final ChannelPoolMap<InetSocketAddress, SimpleChannelPool> singleClientChannelPoolMap = new AbstractChannelPoolMap<InetSocketAddress, SimpleChannelPool>() {
        @Override
        protected SimpleChannelPool newPool(InetSocketAddress key) {
            //FixedChannelPool(Bootstrap bootstrap, 引导类
            //                            ChannelPoolHandler handler, handler 的创建类
            //                            ChannelHealthChecker healthCheck, 健康检查
            //                            AcquireTimeoutAction action,
            //                            final long acquireTimeoutMillis,
            //                            int maxConnections, 最大连接数
            //                            int maxPendingAcquires, 等待连接相关
            //                            boolean releaseHealthCheck, 释放检查
            //                            boolean lastRecentUsed) 获取连接的规则 FIFO/LIFO
            return new FixedChannelPool(bootstrap.remoteAddress(key), new IMClientChannelPoolHandler(), ChannelHealthChecker.ACTIVE, null, -1, IMServerContext.SERVER_CONFIG.getClusterChannelPoolCoreConnection(), Integer.MAX_VALUE, true, false);
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
            int port = IMServerContext.SERVER_CONFIG.getPort();
            // 获取本机ip与端口号
            String localServerAddress0 = ImConstant.LOCAL_HOST + ImConstant.COLON_SPLIT + port;
            // 排除本机
            if  (!localServerAddress0.equals(serverAddress) && !IMServerContext.LOCAL_ADDRESS.equals(serverAddress)) {
                final InetSocketAddress inetSocketAddress = SocketAddressUtil.convert2SocketAddress(serverAddress);
                // 获取对应的缓存类型来进行判断存储
                SimpleChannelPool simpleChannelPool = singleClientChannelPoolMap.get(inetSocketAddress);
                // 默认一开始所有的连接都是存活的, 并且缓存所有连接
                IMServerContext.CLUSTER_GLOBAL_SERVER_CONNECTS_CACHE.put(inetSocketAddress, simpleChannelPool);
                IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.put(inetSocketAddress, simpleChannelPool);
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
        workGroup.shutdownGracefully();
    }
}
