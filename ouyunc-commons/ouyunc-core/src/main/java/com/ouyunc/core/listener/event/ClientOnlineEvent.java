package com.ouyunc.core.listener.event;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fzx
 * @Description: 客户端上线事件
 **/
public class ClientOnlineEvent extends MessageEvent {

    /**
     * channel 上下文
     */
    private final ChannelHandlerContext ctx;

    public ClientOnlineEvent(Object source, ChannelHandlerContext ctx) {
        super(source);
        this.ctx = ctx;
    }


    public ClientOnlineEvent(Object source, ChannelHandlerContext ctx, long publishTime) {
        super(source, publishTime);
        this.ctx = ctx;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return this.ctx;
    }
}
