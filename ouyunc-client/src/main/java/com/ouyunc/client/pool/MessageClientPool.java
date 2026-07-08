package com.ouyunc.client.pool;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.SocketAddressUtil;
import com.ouyunc.client.base.ChannelPoolKey;
import com.ouyunc.core.properties.MessageProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 这里使用客户端连接池来进行使用多个通道连接每个集群中的服务器端
 **/
public class MessageClientPool {
    private static final Logger log = LoggerFactory.getLogger(MessageClientPool.class);

    private static final String loopBackAddress = "127.0.0.1";

    private static final Bootstrap bootstrap;
    private static final EventLoopGroup workGroup;
    public static final ChannelPoolMap<ChannelPoolKey, SimpleChannelPool> clientSimpleChannelPoolMap = new AbstractChannelPoolMap<>() {
        @Override
        protected SimpleChannelPool newPool(ChannelPoolKey clientChannelPool) {
            // 将协议设置进去,在创建channel 创建的地方取出来，进行动态的设置
            bootstrap.attr(AttributeKey.valueOf(MessageConstant.BOOTSTRAP_ATTR_KEY_TAG_CLIENT), clientChannelPool.getProtocol());
            // 根据不同的参数设置不同的bootstrap 属性值，对应不同的协议channelPool
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
            return new FixedChannelPool(bootstrap.remoteAddress(SocketAddressUtil.convert2SocketAddress(clientChannelPool.getServerAddress())), new MessageClientChannelPoolHandler(), ChannelHealthChecker.ACTIVE, FixedChannelPool.AcquireTimeoutAction.NEW, 10000, 1, 3, true, false);
        }
    };

    // 初始化
    static {
        bootstrap = new Bootstrap();
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors()*2);
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
    }

    /**
     * @Author fzx
     * @Description 使用连接池初始化内置客户端，动态扩容缩容连接
     */
    public static void init(MessageProperties serverProperties) {

    }


    /**
     * @Author fzx
     * @Description 注销内置客户端
     */
    public static void stop() {
        if (workGroup != null) {
            workGroup.shutdownGracefully();
        }
    }
}
