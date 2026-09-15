package com.ouyunc.client.pool;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.SocketAddressUtil;
import com.ouyunc.client.base.ChannelPoolKey;
import com.ouyunc.core.properties.MessageProperties;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.pool.AbstractChannelPoolMap;
import io.netty.channel.pool.ChannelHealthChecker;
import io.netty.channel.pool.ChannelPoolMap;
import io.netty.channel.pool.FixedChannelPool;
import io.netty.channel.pool.SimpleChannelPool;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 客户端连接池；新建池时 clone Bootstrap，避免共享实例并发污染地址/协议。
 **/
public class MessageClientPool {
    private static final Logger log = LoggerFactory.getLogger(MessageClientPool.class);

    private static Bootstrap bootstrap;
    private static EventLoopGroup workGroup;
    private static volatile String defaultServerAddress;

    public static final ChannelPoolMap<ChannelPoolKey, SimpleChannelPool> clientSimpleChannelPoolMap =
            new AbstractChannelPoolMap<>() {
                @Override
                protected SimpleChannelPool newPool(ChannelPoolKey clientChannelPool) {
                    ensureBootstrap();
                    // clone 后再设地址/协议，避免多协议/多目标并发互相覆盖
                    Bootstrap peerBootstrap = bootstrap.clone()
                            .remoteAddress(SocketAddressUtil.convert2SocketAddress(clientChannelPool.getServerAddress()))
                            .attr(AttributeKey.valueOf(MessageConstant.BOOTSTRAP_ATTR_KEY_TAG_CLIENT),
                                    clientChannelPool.getProtocol());
                    return new FixedChannelPool(peerBootstrap, new MessageClientChannelPoolHandler(),
                            ChannelHealthChecker.ACTIVE, FixedChannelPool.AcquireTimeoutAction.NEW,
                            10000, 1, 3, true, false);
                }
            };

    /**
     * 使用连接池初始化客户端；记录默认远端地址供模板发送使用。
     */
    public static synchronized void init(MessageProperties messageProperties) {
        ensureBootstrap();
        if (messageProperties != null && StringUtils.isNotBlank(messageProperties.getLocalServerAddress())) {
            defaultServerAddress = messageProperties.getLocalServerAddress();
            log.info("客户端连接池默认远端地址: {}", defaultServerAddress);
        }
    }

    public static String defaultServerAddress() {
        return defaultServerAddress;
    }

    private static synchronized void ensureBootstrap() {
        if (bootstrap != null) {
            return;
        }
        workGroup = new NioEventLoopGroup(Runtime.getRuntime().availableProcessors() * 2);
        bootstrap = new Bootstrap();
        bootstrap.group(workGroup)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.SO_REUSEADDR, true)
                .option(ChannelOption.SO_KEEPALIVE, true)
                .option(ChannelOption.TCP_NODELAY, true);
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
