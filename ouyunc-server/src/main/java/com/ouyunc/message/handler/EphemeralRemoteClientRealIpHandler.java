package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IpUtil;
import io.netty.channel.ChannelHandlerContext;
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
        if (msg instanceof HAProxyMessage proxyMessage) {
            // only save client real ip
            log.info("proxy message is : {}", msg);
            String clientRealIp = proxyMessage.sourceAddress();
            // 存入ctx 中，注意不能跨服务从ctx 获取该值，后面会解析处理存到packet中传递
            if (StringUtils.isNoneBlank(clientRealIp)) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP, clientRealIp);
            }
        }else if (msg instanceof FullHttpRequest request) {
            // 工具类获取真实的客户端ip
            String clientRealIp = IpUtil.getIpFromHttpHeaders(request.headers());
            if (StringUtils.isNoneBlank(clientRealIp)) {
                ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_CLIENT_REAL_IP, clientRealIp);
            }
            ctx.fireChannelRead(request.retain());
        }
        // 移除该处理器
        ctx.pipeline().remove(this);
    }
}
