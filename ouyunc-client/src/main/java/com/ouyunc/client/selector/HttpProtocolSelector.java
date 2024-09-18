package com.ouyunc.client.selector;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.client.handler.HttpProtocolHandler;
import io.netty.channel.Channel;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URISyntaxException;

/**
 * @author fzx
 * @description http 协议
 */
public class HttpProtocolSelector implements ProtocolSelector<Protocol, Channel> {
    private static final Logger log = LoggerFactory.getLogger(HttpProtocolSelector.class);

    @Override
    public boolean match(Protocol protocol) {
        // 判断是何种协议,注意这里不可以使用  in.readByte();
        return ProtocolTypeEnum.HTTP.getProtocol() == protocol.getProtocol() && ProtocolTypeEnum.HTTP.getProtocolVersion() == protocol.getProtocolVersion();
    }

    /**
     * @Author fzx
     * @Description 处理该协议对应的handler
     */
    @Override
    public void process(Channel channel) throws URISyntaxException {
        channel.pipeline()
                .addLast(MessageConstant.HTTP_SERVER_CODEC_HANDLER, new HttpServerCodec())
                .addLast(MessageConstant.CHUNKED_WRITE_HANDLER, new ChunkedWriteHandler())
                .addLast(MessageConstant.HTTP_OBJECT_AGGREGATOR_HANDLER, new HttpObjectAggregator(Integer.MAX_VALUE))
                // 这一步没有加自定义编解码器，是因为上面的处理器已经处理了消息编解码
                // http 协议处理器
                .addLast(MessageConstant.HTTP_DISPATCHER_HANDLER, new HttpProtocolHandler());

    }

}
