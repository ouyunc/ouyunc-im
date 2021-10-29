package com.ouyu.im.channel;

import com.ouyu.im.constant.CacheConstant;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.dispatcher.ProtocolDispatcher;
import com.ouyu.im.entity.ChannelUserInfo;
import com.ouyu.im.utils.SslUtil;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.SSLEngine;

/**
 * @Author fangzhenxun
 * @Description: 默认初始化 socket channel
 * @Version V1.0
 **/
public class DefaultSocketChannelInitializer extends SocketChannelInitializer{
    private static Logger log = LoggerFactory.getLogger(DefaultSocketChannelInitializer.class);


    /**
     * @Author fangzhenxun
     * @Description 初始化 socket channel
     * @param socketChannel
     * @return void
     */
    void initSocketChannel(SocketChannel socketChannel) {
        // 这里只设置协议分发器，具体可参照netty 的源码例子
        ChannelPipeline pipeline = socketChannel.pipeline();
        // 是否开启SSL/TLS
        if (IMServerContext.SERVER_CONFIG.isSslEnable()) {
            // 这个处理器需要放到第一位
            SslUtil.configSSL(ch -> {
                SSLEngine sslEngine = SslUtil.buildServerSslContext().newEngine(socketChannel.alloc());
                // 服务器端模式，客户端模式设置为true
                sslEngine.setUseClientMode(false);
                // 不需要验证客户端，客户端不设置该项；  SSL/TLS 开启后有多种认证方式：1-不需要认证，2-单向认证（一般是客户端认证），3-双向认证
                sslEngine.setNeedClientAuth(false);
                socketChannel.pipeline().addFirst(ImConstant.SSL, new SslHandler(sslEngine));
            }, socketChannel);

        }
        // 这里为了解决粘包拆包的问题，当第一次数据到达协议分发器时应该是一个完成的包packet
        // 也可以让ProtocolDispatcher继承LengthFieldBasedFrameDecoder来实现半包粘包，考虑到其他协议这里使用继承的方式 @todo （暂时不知道会不会有问题）
        //.addLast(new LengthFieldBasedFrameDecoder())
        pipeline.addLast(ImConstant.PROTOCOL_DISPATCHER, new ProtocolDispatcher());



        // 添加监听器,如果有外部客户端channel 关闭则处理登出逻辑
        socketChannel.closeFuture().addListener(new ChannelFutureListener() {
            @Override
            public void operationComplete(ChannelFuture future) throws Exception {
                if (future.isDone()) {
                    if (future.isSuccess()) {
                        log.info("================外部客户端关闭了:{}==================", socketChannel.id().asShortText());
                        //1,从channel中的attrMap取出相关属性
                        AttributeKey<ChannelUserInfo> channelTagLoginKey = AttributeKey.valueOf(ImConstant.CHANNEL_TAG_LOGIN);
                        final ChannelUserInfo authenticationUserInfo = socketChannel.attr(channelTagLoginKey).get();
                        if (authenticationUserInfo != null) {
                            IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.USER_COMMON_CACHE_PREFIX + CacheConstant.LOGIN_CACHE_PREFIX + authenticationUserInfo.getIdentity());
                            IMServerContext.LOCAL_USER_CHANNEL_CACHE.invalidate(authenticationUserInfo.getIdentity());
                        }
                    }
                }
            }
        });
    }


}
