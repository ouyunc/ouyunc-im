package com.ouyunc.im.channel;

import io.netty.channel.ServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 默认初始化 server channel
 **/
public class DefaultServerChannelInitializer extends ServerChannelInitializer {
    private static Logger log = LoggerFactory.getLogger(DefaultServerChannelInitializer.class);


    /**
     * @param serverChannel
     * @return void
     * @Author fangzhenxun
     * @Description 初始化 服务端 channel
     */
    @Override
    void initServerChannel(ServerChannel serverChannel) {
        // do nothing
    }
}
