package com.ouyunc.im.dispatcher;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fangzhenxun
 * @Description: 调度处理器策略
 **/
public interface DispatcherProcessorStrategy {

    /**
     * @param ctx
     * @return void
     * @Author fangzhenxun
     * @Description 处理逻辑
     */
    void doProcess(ChannelHandlerContext ctx);
}
