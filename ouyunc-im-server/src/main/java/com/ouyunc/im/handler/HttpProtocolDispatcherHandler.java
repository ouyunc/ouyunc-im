package com.ouyunc.im.handler;

import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.utils.MapUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;


/**
 * @Author fangzhenxun
 * @Description: http 调度处理器
 **/
public class HttpProtocolDispatcherHandler extends SimpleChannelInboundHandler<Object> {
    private static Logger log = LoggerFactory.getLogger(HttpProtocolDispatcherHandler.class);

    /**
     * @Author fangzhenxun
     * @Description 处理http类协议
     * @param ctx
     * @param msg
     * @return void
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 判断该消息是http何种变种协议
        if (msg instanceof FullHttpRequest) {
            FullHttpRequest request = (FullHttpRequest) msg;
            String uriStr = request.uri();
            log.info("=================当前请求路径uri：{}=============================",uriStr);
            // 判断是否是websocket 的101 升级请求，如果是则升级为websocket协议
            if (isUpgradeToWebSocket(request)) {
                URI uri = new URI(uriStr);
                //封装参数传
                Map<String, Object> queryParamsMap = MapUtil.wrapParams2Map(uri.getQuery());
                //重设uri
                request.setUri(uri.getPath());
                Protocol.prototype(Protocol.WS.getProtocol(), Protocol.WS.getVersion()).doDispatcher(ctx, queryParamsMap);
                //如果请求是一次升级了的 WebSocket 请求，则递增引用计数器（retain）并且将它传递给在 ChannelPipeline 中的下个 ChannelInboundHandler
                ctx.fireChannelRead(request.retain());
            }else {
                // 处理http 通用请求
                log.info("=================开始处理http请求=============================");
            }
        }
        // websocket消息，直接传到下面一个handler去处理
        if (msg instanceof WebSocketFrame) {
            ctx.fireChannelRead(((WebSocketFrame) msg).retain());
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 判断当前http 请求是何种作用
     * @param request
     * @return boolean
     */
    protected boolean isUpgradeToWebSocket(FullHttpRequest request) {
        HttpHeaders httpHeaders = request.headers();
        return IMConstant.WEBSOCKET_PROTOCOL_CONNECTION.equalsIgnoreCase(httpHeaders.get(HttpHeaderNames.CONNECTION)) && IMConstant.WEBSOCKET_PROTOCOL_UPGRADE.equalsIgnoreCase(httpHeaders.get(HttpHeaderNames.UPGRADE));
    }
}
