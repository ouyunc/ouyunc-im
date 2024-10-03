package com.ouyunc.core.listener.event;

import io.netty.channel.ChannelHandlerContext;

/**
 * @Author fzx
 * @Description: 客户端登录成功事件
 **/
public class ClientLoginEvent extends MessageEvent {

    /**
     * channel 上下文
     */
    private final ChannelHandlerContext ctx;

    public ClientLoginEvent(Object source, ChannelHandlerContext ctx) {
        super(source);
        this.ctx = ctx;
    }


    public ClientLoginEvent(Object source, ChannelHandlerContext ctx, long publishTime) {
        super(source, publishTime);
        this.ctx = ctx;
    }

    public ChannelHandlerContext getChannelHandlerContext() {
        return this.ctx;
    }
}
