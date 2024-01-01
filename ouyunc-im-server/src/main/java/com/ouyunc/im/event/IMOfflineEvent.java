package com.ouyunc.im.event;

import io.netty.channel.ChannelHandlerContext;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: 用户离线事件
 **/
public class IMOfflineEvent extends IMEvent {

    /**
     * channel 上下文
     */
    private ChannelHandlerContext ctx;

    public IMOfflineEvent(Object source) {
        super(source);
    }

    public IMOfflineEvent(Object source, ChannelHandlerContext ctx) {
        super(source);
        this.ctx = ctx;
    }

    public IMOfflineEvent(Object source, Clock clock) {
        super(source, clock);
    }

    public IMOfflineEvent(Object source, ChannelHandlerContext ctx, Clock clock) {
        super(source, clock);
        this.ctx = ctx;
    }

}
