package com.ouyu.im.designpattern.strategy.dispatcher;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fangzhenxun
 * @Description: 调度处理器策略
 * @Version V1.0
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
