package com.ouyunc.message.handler;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.MapUtil;
import com.ouyunc.core.codec.MqttWebSocketCodec;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrameAggregator;
import io.netty.handler.codec.http.websocketx.WebSocketServerProtocolHandler;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketServerCompressionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.Map;


/**
 * @Author fzx
 * @Description: http 调度处理器
 **/
public class HttpProtocolDispatcherHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(HttpProtocolDispatcherHandler.class);

    /**
     * @Author fzx
     * @Description 处理http类协议
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        // 判断该消息是http何种变种协议
        if (msg instanceof FullHttpRequest request) {
            // 获取真实ip 并设置
            String uriStr = request.uri();
            log.info("当前请求路径uri：{}", uriStr);
            URI uri = new URI(uriStr);
            //封装参数传
            Map<String, Object> queryParamsMap = MapUtil.wrapParams2Map(uri.getQuery());
            // 判断是否是websocket 的101 升级请求，如果是则升级为websocket协议
            if (isUpgradeToWebSocket(request)) {
                //重设uri,去除请求param参数,否则ws会报错
                request.setUri(uri.getPath());
                // 获取websocket 子协议
                String secWebsocketProtocol = request.headers().get(MessageConstant.SEC_WEBSOCKET_PROTOCOL);
                // 判断各种子协议并处理，目前这里只判断是否建立在websocket之上的mqtt协议
                if (MessageConstant.MQTT.equals(secWebsocketProtocol) || MessageConstant.MQTT_3_1.equals(secWebsocketProtocol)) {
                    ctx.pipeline()
                            //10 * 1024 * 1024
                            .addLast(MessageConstant.WS_FRAME_AGGREGATOR_HANDLER, new WebSocketFrameAggregator(Integer.MAX_VALUE))
                            .addLast(MessageConstant.WS_COMPRESSION_HANDLER, new WebSocketServerCompressionHandler())
                            //10485760
                            .addLast(MessageConstant.WS_SERVER_PROTOCOL_HANDLER, new WebSocketServerProtocolHandler(MessageServerContext.serverProperties().getWebsocketPath(), MessageConstant.MQTT_WEBSOCKET_SUB_PROTOCOLS, true, Integer.MAX_VALUE))
                            // mqtt websocket 编解码器
                            .addLast(MessageConstant.MQTT_WEBSOCKET_CODEC_HANDLER, new MqttWebSocketCodec());
                    MessageServerContext.findProtocol(NativePacketProtocol.MQTT.getProtocol(), NativePacketProtocol.MQTT.getProtocolVersion()).doDispatcher(ctx, queryParamsMap);
                } else {
                    MessageServerContext.findProtocol(NativePacketProtocol.WS.getProtocol(), NativePacketProtocol.WS.getProtocolVersion()).doDispatcher(ctx, queryParamsMap);
                }
                //如果请求是一次升级了的 WebSocket 请求，则递增引用计数器（retain）并且将它传递给在 ChannelPipeline 中的下个 ChannelInboundHandler
                ctx.fireChannelRead(request.retain());
            } else {
                // 处理http 通用请求
                MessageServerContext.findProtocol(NativePacketProtocol.HTTP.getProtocol(), NativePacketProtocol.HTTP.getProtocolVersion()).doDispatcher(ctx, queryParamsMap);
            }
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
        HttpHeaders httpHeaders = request.headers();
        return MessageConstant.WEBSOCKET_PROTOCOL_CONNECTION.equalsIgnoreCase(httpHeaders.get(HttpHeaderNames.CONNECTION)) && MessageConstant.WEBSOCKET_PROTOCOL_UPGRADE.equalsIgnoreCase(httpHeaders.get(HttpHeaderNames.UPGRADE));
    }
}
