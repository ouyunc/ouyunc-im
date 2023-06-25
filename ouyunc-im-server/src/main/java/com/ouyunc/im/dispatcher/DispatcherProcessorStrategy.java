package com.ouyunc.im.dispatcher;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fangzhenxun
 * @Description: 调度处理器策略
 **/
public interface DispatcherProcessorStrategy {

    /**
     * @Author fangzhenxun
     * @Description 处理逻辑
     * @param ctx
     * @return void
     */
    void doProcess(ChannelHandlerContext ctx);
}
