package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IpUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.support.ProxyProtocolTrustSupport;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.haproxy.HAProxyMessage;
import io.netty.handler.codec.http.FullHttpRequest;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * ephemeral远端客户端真实ip 获取, 只处理一次
 */
public class EphemeralRemoteClientRealIpHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(EphemeralRemoteClientRealIpHandler.class);

    /**
     * 获取远端客户端真实ip, 注意：客户端ip 可能被伪造，这里不做分析处理；可以优化将下面的if 改写成策略，让各个ProtocolDispatcherProcessor的子类去实现具体的逻辑，目前暂不做优化处理
     * @param ctx
     * @param msg
     * @throws Exception
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        try {
            if (msg instanceof HAProxyMessage proxyMessage) {
                if (!ProxyProtocolTrustSupport.isTrustedProxy(
                        ctx.channel().remoteAddress(),
                        MessageServerContext.serverProperties().getProxyProtocolTrustedProxies())) {
                    log.warn("拒绝非可信代理发送的 PROXY Protocol 连接，remote={}", ctx.channel().remoteAddress());
                    ctx.close();
                    return;
                }
                String clientRealIp = proxyMessage.sourceAddress();
                if (StringUtils.isNoneBlank(clientRealIp)) {
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP, clientRealIp);
                }
                ChannelPipeline pipeline = ctx.pipeline();
                if (pipeline.get(MessageConstant.HA_PROXY_PROTOCOL_DECODER_HANDLER) != null) {
                    // 摘掉 decoder，把 cumulation 里 PROXY 头之后的应用层字节交给后面的 ProtocolDispatcher
                    pipeline.remove(MessageConstant.HA_PROXY_PROTOCOL_DECODER_HANDLER);
                }
            } else if (msg instanceof FullHttpRequest request) {
                // PROXY Protocol 的来源已经通过 TCP 对端校验，不能再被客户端可伪造的 HTTP 头覆盖。
                String proxyProtocolIp = ChannelAttrUtil.getChannelAttribute(
                        ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP);
                if (StringUtils.isBlank(proxyProtocolIp)) {
                    String clientRealIp = IpUtil.getIpFromHttpHeaders(request.headers());
                    if (StringUtils.isNoneBlank(clientRealIp)) {
                        ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP, clientRealIp);
                    }
                }
                ctx.fireChannelRead(request.retain());
            } else if (msg instanceof ByteBuf buf) {
                ctx.fireChannelRead(buf.retain());
            }
        } finally {
            if (ctx.pipeline().context(this) != null) {
                ctx.pipeline().remove(this);
            }
        }
    }
}
