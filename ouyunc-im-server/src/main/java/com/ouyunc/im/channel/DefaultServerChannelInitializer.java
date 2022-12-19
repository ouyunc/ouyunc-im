package com.ouyunc.im.channel;

import io.netty.channel.ServerChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: 默认初始化 server channel
 * @Version V3.0
 **/
public class DefaultServerChannelInitializer extends ServerChannelInitializer{
    private static Logger log = LoggerFactory.getLogger(DefaultServerChannelInitializer.class);


    /**
     * @Author fangzhenxun
     * @Description 初始化 服务端 channel
     * @param serverChannel
     * @return void
     */
    @Override
    void initServerChannel(ServerChannel serverChannel) {
        // do nothing
    }
}
