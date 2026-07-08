package com.ouyunc.core.listener.event.payload;

import io.netty.channel.ChannelHandlerContext;

/**
 *  source 载体（登录信息与通道上下文）。
 */
public record ClientLoginEventPayload(Object loginInfo, ChannelHandlerContext ctx) {
}
