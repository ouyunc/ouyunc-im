package com.ouyunc.im.dispatcher;

import com.ouyunc.im.codec.PacketCodec;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.handler.HttpProtocolDispatcherHandler;
import com.ouyunc.im.handler.MqttProtocolDispatcherHandler;
import com.ouyunc.im.handler.PacketProtocolDispatcherHandler;
import com.ouyunc.im.protocol.Protocol;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.HttpServerCodec;
import io.netty.handler.stream.ChunkedWriteHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;

/**
 * @Author fangzhenxun
 * @Description: mqtt 协议
 **/
public class MqttDispatcherProcessorStrategy implements DispatcherProcessorStrategy{
    private static Logger log = LoggerFactory.getLogger(MqttDispatcherProcessorStrategy.class);

    /**
     * @Author fangzhenxun
     * @Description 在这里进行处理原生mqtt协议以及不同版本
     * @param ctx
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        log.info("mqtt 协议分发器 MqttDispatcherProcessorStrategy 正在处理...");
        ctx.pipeline()
                .addLast(IMConstant.HTTP_DISPATCHER_HANDLER, new MqttProtocolDispatcherHandler())
                .remove(IMConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active
        ctx.fireChannelActive();
    }
}
