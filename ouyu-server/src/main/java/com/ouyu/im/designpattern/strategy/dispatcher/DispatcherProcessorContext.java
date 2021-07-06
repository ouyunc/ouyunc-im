package com.ouyu.im.designpattern.strategy.dispatcher;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fangzhenxun
 * @Description: 上下文
 * @Version V1.0
 **/
public class DispatcherProcessorContext {

    private DispatcherProcessorStrategy dispatcherProcessorStrategy;

    public DispatcherProcessorContext(DispatcherProcessorStrategy dispatcherProcessorStrategy) {
        this.dispatcherProcessorStrategy = dispatcherProcessorStrategy;
    }

    public void process(ChannelHandlerContext ctx) {
        this.dispatcherProcessorStrategy.doProcess(ctx);
    }

}
