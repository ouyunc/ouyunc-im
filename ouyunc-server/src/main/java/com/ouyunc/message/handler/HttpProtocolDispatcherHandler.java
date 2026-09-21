package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: http 调度处理器
 **/
public class HttpProtocolDispatcherHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(HttpProtocolDispatcherHandler.class);

    /**
     * @Author fzx
     * @Description 处理http类协议；
     * 注意：这里可以提前做一些appKey 的鉴权（防止客户端只连接不登录，白白占用连接资源）以及appKey下连接数的统计或限制；
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 判断该消息是http何种变种协议
        if (msg instanceof FullHttpRequest request) {
            // 获取真实ip 并设置
            //封装参数传
            // 判断是否是websocket 的101 升级请求，如果是则升级为websocket协议
            if (isUpgradeToWebSocket(request)) {
                // IM WS：保留 query 给握手校验；异步路径自己 retain 并 fireChannelRead
                MessageServerContext.findProtocol(NativePacketProtocol.WS.getProtocol(), NativePacketProtocol.WS.getProtocolVersion()).doDispatcher(ctx, request);
                return;
            }
            // 处理http 通用请求
            MessageServerContext.findProtocol(NativePacketProtocol.HTTP.getProtocol(), NativePacketProtocol.HTTP.getProtocolVersion()).doDispatcher(ctx, request);
        }
        // websocket消息，直接传到下面一个handler去处理
        if (msg instanceof WebSocketFrame) {
            ctx.fireChannelRead(((WebSocketFrame) msg).retain());
        }
    }


    /**
     * @Author fzx
     * @Description 判断当前http 请求是何种作用
     */
    protected boolean isUpgradeToWebSocket(FullHttpRequest request) {
        HttpHeaders headers = request.headers();
        return headers.containsValue(HttpHeaderNames.CONNECTION, HttpHeaderValues.UPGRADE, true)
                && headers.contains(HttpHeaderNames.UPGRADE, HttpHeaderValues.WEBSOCKET, true);
    }
}
