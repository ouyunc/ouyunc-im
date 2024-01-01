package com.ouyunc.im.event;

import io.netty.channel.ChannelHandlerContext;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: 用户上线事件
 **/
public class IMOnlineEvent extends IMEvent {

    /**
     * channel 上下文
     */
    private ChannelHandlerContext ctx;

    public IMOnlineEvent(Object source) {
        super(source);
    }

    public IMOnlineEvent(Object source, ChannelHandlerContext ctx) {
        super(source);
        this.ctx = ctx;
    }

    public IMOnlineEvent(Object source, Clock clock) {
        super(source, clock);
    }

    public IMOnlineEvent(Object source, ChannelHandlerContext ctx, Clock clock) {
        super(source, clock);
        this.ctx = ctx;
    }

}
