package com.ouyunc.im.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: socket channel 的初始化
 **/
public abstract class SocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static Logger log = LoggerFactory.getLogger(SocketChannelInitializer.class);

    /**
     * @param socketChannel
     * @return void
     * @Author fangzhenxun
     * @Description 子类只需要覆盖这个方法就可以
     */
    abstract void initSocketChannel(SocketChannel socketChannel);


    /**
     * @param socketChannel
     * @return void
     * @Author fangzhenxun
     * @Description 父类会调用这个方法
     */
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        initSocketChannel(socketChannel);
    }
}
