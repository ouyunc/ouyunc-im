package com.ouyunc.im.event;

import io.netty.channel.ChannelHandlerContext;

import java.time.Clock;

/**
 * @Author fangzhenxun
 * @Description: im 消息转成功换成 packet 事件
 **/
public class IMWritePacketEvent extends IMEvent {

    /**
     * channel 上下文
     */
    private ChannelHandlerContext ctx;

    public IMWritePacketEvent(Object source) {
        super(source);
    }

    public IMWritePacketEvent(Object source, ChannelHandlerContext ctx) {
        super(source);
        this.ctx = ctx;
    }

    public IMWritePacketEvent(Object source, Clock clock) {
        super(source, clock);
    }

    public IMWritePacketEvent(Object source, ChannelHandlerContext ctx, Clock clock) {
        super(source, clock);
        this.ctx = ctx;
    }

}
