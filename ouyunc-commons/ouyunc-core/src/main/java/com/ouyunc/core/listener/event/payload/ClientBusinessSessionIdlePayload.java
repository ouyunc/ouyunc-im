package com.ouyunc.core.listener.event.payload;

import com.ouyunc.base.model.LoginClientInfo;
import io.netty.channel.ChannelHandlerContext;

/**
 * {@link com.ouyunc.base.constant.enums.MessageEventTypeEnum#CLIENT_BUSINESS_SESSION_IDLE} 的 source 载体。
 * {@code strike} 为连续业务读空闲次数（递增）；{@code ctx} 供监听方主动 {@link ChannelHandlerContext#close()} 等操作。
 */
public record ClientBusinessSessionIdlePayload(LoginClientInfo loginInfo, int strike, ChannelHandlerContext ctx) {
}
