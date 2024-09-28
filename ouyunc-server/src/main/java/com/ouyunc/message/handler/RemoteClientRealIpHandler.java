package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCounted;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 远端客户端真实ip 获取
 */
public class RemoteClientRealIpHandler  extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(RemoteClientRealIpHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        if (msg instanceof HAProxyMessage proxyMessage) {
            // only save client real ip
            log.info("proxy message is : {}", msg);
            String clientRealIp = proxyMessage.sourceAddress();
            // 存入ctx 中，注意不能跨服务从ctx 获取该值，后面会解析处理存到packet中传递
            AttributeKey<String> proxyMessageKey = AttributeKey.valueOf(MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP);
            ctx.channel().attr(proxyMessageKey).set(clientRealIp);
            ctx.pipeline().remove(this);
            return;
        }
        if (msg instanceof ReferenceCounted in) {
            ctx.fireChannelRead(in.retain());
        }
    }
}
