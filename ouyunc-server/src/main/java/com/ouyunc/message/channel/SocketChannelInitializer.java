package com.ouyunc.message.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;

/**
 * @Author fzx
 * @Description: socket channel 的初始化
 **/
public abstract class SocketChannelInitializer extends ChannelInitializer<SocketChannel> {

    /**
     * @Author fzx
     * @Description 子类只需要覆盖这个方法就可以
     */
    abstract void initSocketChannel(SocketChannel socketChannel);


    /**
     * @Author fzx
     * @Description 父类会调用这个方法
     */
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        initSocketChannel(socketChannel);
    }
}
