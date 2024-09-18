package com.ouyunc.message.cluster.client.handler;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.SSLUtil;
import com.ouyunc.core.codec.PacketCodec;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.AbstractChannelPoolHandler;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 也可以通过继承AbstractChannelPoolHandler 来实现动态的channel 管理与释放，集群内置客户端的管道处理
 **/
public class MessageClientChannelPoolHandler extends AbstractChannelPoolHandler {
    private static final Logger log = LoggerFactory.getLogger(MessageClientChannelPoolHandler.class);


    /**
     * @Author fzx
     * @Description channel 的创建,初次与服务端建立连接的时候会创建channel，内置客户端只做发送处理，不牵涉到粘包，半包，拿到的就是一个完整的包
     * 这个链接池，所有协议以及包类型的信息都会走这里
     */
    @Override
    public void channelCreated(Channel channel) throws Exception {
        log.info("内部客户端channel池，创建通道channel: {}", channel.id().asShortText());
        ChannelPipeline pipeline = channel.pipeline();
        if (MessageServerContext.serverProperties().isSslEnable()) {
            // 这个处理器需要放到第一位
            SSLUtil.configSSL(ch -> {
                SSLEngine sslEngine = SSLUtil.buildClientSslContext(MessageServerContext.serverProperties().getSslCertificate(), MessageServerContext.serverProperties().getSslPrivateKey()).newEngine(channel.alloc());
                // 客户端模式
                sslEngine.setUseClientMode(true);
                // 不进行客户端校验
                sslEngine.setNeedClientAuth(false);
                pipeline.addFirst(new SslHandler(sslEngine));
            }, channel);
        }
        // 添加消息处理器链
        pipeline.addLast(MessageConstant.LOG_HANDLER, new LoggingHandler(MessageServerContext.serverProperties().getLogLevel()))
                // 编解码
                .addLast(MessageConstant.CLIENT_PACKET_CODEC_HANDLER, new PacketCodec())
                // 开启Netty自带的心跳处理器，每5秒发送一次心跳，用来做动态channel池处理
                .addLast(MessageConstant.CLIENT_IDLE_HANDLER, new IdleStateHandler(MessageServerContext.serverProperties().getClusterClientIdleReadTimeout(), MessageServerContext.serverProperties().getClusterClientIdleWriteTimeout(), MessageServerContext.serverProperties().getClusterClientIdleReadWriteTimeout(), TimeUnit.SECONDS))
                // 心跳检测（动态channel）
                .addLast(MessageConstant.CLIENT_HEART_BEAT_HANDLER, new MessageClientHeartBeatHandler());

        // 添加监听器,如果有内部客户端channel 关闭，则移除channel，（包括动态关闭核心channel）
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    if (future.isSuccess()) {
                        // 从该channel中取出标签
                        AttributeKey<Integer> channelPoolHashCodeKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_POOL);
                        Integer channelPoolHashCode = channel.attr(channelPoolHashCodeKey).get();
                        if (channelPoolHashCode != null) {
                            // 关闭channel 并尝试并移除内部客户端核心channel
                            MessageServerContext.clusterClientCoreChannelPoolCache.get(channelPoolHashCode).remove(channel);
                        }
                    }
                }
            }
        });

    }

}
