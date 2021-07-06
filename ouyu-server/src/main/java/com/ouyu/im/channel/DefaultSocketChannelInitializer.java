package com.ouyu.im.channel;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.context.IMContext;
import com.ouyu.im.dispatcher.ProtocolDispatcher;
import com.ouyu.im.entity.ChannelUserInfo;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.socket.SocketChannel;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        socketChannel.pipeline()
                // 这里为了解决粘包拆包的问题，当第一次数据到达协议分发器时应该是一个完成的包packet
                // 也可以让ProtocolDispatcher继承LengthFieldBasedFrameDecoder来实现半包粘包，考虑到其他协议这里使用继承的方式 @todo （暂时不知道会不会有问题）
                //.addLast(new LengthFieldBasedFrameDecoder())
                .addLast(ImConstant.PROTOCOL_DISPATCHER, new ProtocolDispatcher());



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
                            IMContext.LOGIN_USER_INFO_CACHE.delete(authenticationUserInfo.getIdentity());
                            IMContext.LOCAL_USER_CHANNEL_CACHE.invalidate(authenticationUserInfo.getIdentity());
                        }
                    }
                }
            }
        });
    }
}
