package com.ouyunc.message.channel;

import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.MessageLoggingHandler;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ServerChannel;

/**
 * @Author fzx
 * @Description: server channel 初始化, 预留扩展
 **/
public abstract class ServerChannelInitializer extends ChannelInitializer<ServerChannel> {

    /**
     * @Author fzx
     * @Description 子类只需要覆盖这个方法就可以
     */
    abstract void initServerChannel(ServerChannel serverChannel);


    /**
     * @Author fzx
     * @Description 父类会调用这个方法
     */
    @Override
    protected void initChannel(ServerChannel serverChannel) throws Exception {
        // 设置日志
        serverChannel.pipeline().addLast(new MessageLoggingHandler(MessageServerContext.serverProperties().getLogLevel()));
        initServerChannel(serverChannel);
    }
}
