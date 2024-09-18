package com.ouyunc.client.selector;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.client.handler.WsProtocolHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.extensions.compression.WebSocketClientCompressionHandler;
import io.netty.handler.stream.ChunkedWriteHandler;

import java.net.URISyntaxException;

/**
 * @author fzx
 * @description websocket 协议
 */
public class WebsocketProtocolDispatcherProcessor extends HttpProtocolSelector {


    @Override
    public boolean match(Protocol protocol) {
        return ProtocolTypeEnum.WS.getProtocol() == protocol.getProtocol() && ProtocolTypeEnum.WS.getProtocolVersion() == protocol.getProtocolVersion();
    }

    @Override
    public void process(Channel channel) throws URISyntaxException {
        channel.pipeline()
                .addLast(MessageConstant.HTTP_SERVER_CODEC_HANDLER, new HttpClientCodec())
                .addLast(MessageConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(MessageConstant.HTTP_OBJECT_AGGREGATOR_HANDLER, new HttpObjectAggregator(Integer.MAX_VALUE))
                .addLast(WebSocketClientCompressionHandler.INSTANCE)
                .addLast(MessageConstant.WS_HANDLER, new WsProtocolHandler());

        // 握手
    }
}
