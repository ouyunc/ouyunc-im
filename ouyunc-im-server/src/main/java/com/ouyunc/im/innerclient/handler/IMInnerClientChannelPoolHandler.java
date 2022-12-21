package com.ouyunc.im.innerclient.handler;

import com.ouyunc.im.codec.PacketCodec;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.utils.SslUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.pool.ChannelPoolHandler;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import io.netty.handler.ssl.SslHandler;
import io.netty.handler.timeout.IdleStateHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;
import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 通过继承AbstractChannelPoolHandler 来实现动态的channel 管理与释放，集群内置客户端的管道处理
 * @Version V3.0
 **/
public class IMInnerClientChannelPoolHandler implements ChannelPoolHandler {
    private static Logger log = LoggerFactory.getLogger(IMInnerClientChannelPoolHandler.class);


    @Override
    public void channelReleased(Channel ch) throws Exception {
        log.info("客户端释放channel Id: {}",ch.id().asShortText());
    }

    @Override
    public void channelAcquired(Channel ch) throws Exception {
        log.info("从客户端连接池中 获取channel Id: {}",ch.id().asShortText());
    }




    /**
     * @Author fangzhenxun
     * @Description channel 的创建,初次与服务端建立连接的时候会创建channel，内置客户端只做发送处理，不牵涉到粘包，半包，拿到的就是一个完整的包
     * 这个链接池，所有协议以及包类型的信息都会走这里
     * @param channel
     * @return void
     */
    public void channelCreated(Channel channel) throws Exception {
        log.info("创建通道channel: {}", channel.id().asShortText());

        ChannelPipeline pipeline = channel.pipeline();
        if (IMServerContext.SERVER_CONFIG.isSslEnable()) {
            // 这个处理器需要放到第一位
            SslUtil.configSSL(ch -> {
                //@todo 内置客户端SSL/TLS
                SSLEngine sslEngine = SslUtil.buildClientSslContext().newEngine(channel.alloc());
                // 客户端模式
                sslEngine.setUseClientMode(true);
                // 不进行客户端校验
                sslEngine.setNeedClientAuth(false);
                pipeline.addFirst(new SslHandler(sslEngine));
            }, channel);
        }
        // 添加消息处理器链
        pipeline.addLast(IMConstant.LOG, new LoggingHandler(IMServerContext.SERVER_CONFIG.getLogLevel()))
                // 开启Netty自带的心跳处理器，每5秒发送一次心跳，用来做动态channel池处理 @todo ,时间需要调整，以及handler 的顺序
                .addLast(IMConstant.INNER_CLIENT_IDLE, new IdleStateHandler(IMServerContext.SERVER_CONFIG.getClusterInnerClientIdleReadTimeOut(), IMServerContext.SERVER_CONFIG.getClusterInnerClientIdleWriteTimeOut(), IMServerContext.SERVER_CONFIG.getClusterInnerClientIdleReadWriteTimeOut(), TimeUnit.SECONDS))
                // 编解码
                .addLast(IMConstant.INNER_CLIENT_PACKET_CODEC, new PacketCodec())
                // 心跳检测（动态channel）
                .addLast(IMConstant.INNER_CLIENT_HEART_BEAT, new IMInnerClientHeartBeatHandler());

        // 添加监听器,如果有内部客户端channel 关闭，则移除channel，（包括动态关闭核心channel）
        channel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    if (future.isSuccess()) {
                        // 从该channel中取出标签
                        AttributeKey<Integer> channelPoolHashCodeKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_POOL);
                        final Integer channelPoolHashCode = channel.attr(channelPoolHashCodeKey).get();
                        if (channelPoolHashCode != null) {
                            // 关闭channel 并尝试并移除内部客户端核心channel
                            IMServerContext.CLUSTER_INNER_CLIENT_CORE_CHANNEL_POOL.get(channelPoolHashCode).remove(channel);
                        }
                    }
                }
            }
        });

    }

}
