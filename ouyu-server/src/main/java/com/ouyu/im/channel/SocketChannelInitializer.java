package com.ouyu.im.channel;

import io.netty.channel.ChannelInitializer;
import io.netty.channel.socket.SocketChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: socket channel 的初始化
 * @Version V1.0
 **/
public abstract class SocketChannelInitializer extends ChannelInitializer<SocketChannel> {
    private static Logger log = LoggerFactory.getLogger(SocketChannelInitializer.class);

    /**
     * @Author fangzhenxun
     * @Description 子类只需要覆盖这个方法就可以
     * @param socketChannel
     * @return void
     */
    abstract void initSocketChannel(SocketChannel socketChannel);


    /**
     * @Author fangzhenxun
     * @Description 父类会调用这个方法
     * @param socketChannel
     * @return void
     */
    @Override
    protected void initChannel(SocketChannel socketChannel) throws Exception {
        initSocketChannel(socketChannel);
    }
}
