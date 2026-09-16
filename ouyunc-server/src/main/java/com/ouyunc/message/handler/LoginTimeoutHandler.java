package com.ouyunc.message.handler;

import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * MQTT 等不以 LOGIN 包登录的协议：连接激活后启动登录超时。
 */
@ChannelHandler.Sharable
public class LoginTimeoutHandler extends ChannelInboundHandlerAdapter {

    public static final LoginTimeoutHandler INSTANCE = new LoginTimeoutHandler();

    private LoginTimeoutHandler() {
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        LoginTimeoutSupport.install(ctx);
        super.channelActive(ctx);
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        LoginTimeoutSupport.cancel(ctx);
        super.channelInactive(ctx);
    }
}
