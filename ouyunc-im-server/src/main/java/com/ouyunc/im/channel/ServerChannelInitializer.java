package com.ouyunc.im.channel;

import com.ouyunc.im.context.IMServerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ServerChannel;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fangzhenxun
 * @Description: server channel 初始化, 预留扩展
 * @Version V3.0
 **/
public abstract class ServerChannelInitializer extends ChannelInitializer<ServerChannel> {
    private static Logger log = LoggerFactory.getLogger(ServerChannelInitializer.class);

    /**
     * @Author fangzhenxun
     * @Description 子类只需要覆盖这个方法就可以
     * @param serverChannel
     * @return void
     */
    abstract void initServerChannel(ServerChannel serverChannel);


    /**
     * @Author fangzhenxun
     * @Description 父类会调用这个方法
     * @param serverChannel
     * @return void
     */
    @Override
    protected void initChannel(ServerChannel serverChannel) throws Exception {
        // 设置日志
        serverChannel.pipeline().addLast(new LoggingHandler(IMServerContext.SERVER_CONFIG.getLogLevel()));
        initServerChannel(serverChannel);
    }
}
