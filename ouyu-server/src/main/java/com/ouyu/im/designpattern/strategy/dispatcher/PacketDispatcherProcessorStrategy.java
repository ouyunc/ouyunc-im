package com.ouyu.im.designpattern.strategy.dispatcher;

import com.ouyu.im.codec.PacketCodec;
import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.handler.PacketProtocolDispatcherHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.ByteOrder;

/**
 * @Author fangzhenxun
 * @Description: packet 调度处理器
 * @Version V1.0
 **/
public class PacketDispatcherProcessorStrategy implements DispatcherProcessorStrategy{


    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        ctx.pipeline()
                //
                .addLast(ImConstant.LOG, new LoggingHandler(LogLevel.INFO))
                // 粘包半包处理
                .addLast(ImConstant.PACKET_DECODE, new LengthFieldBasedFrameDecoder(ByteOrder.BIG_ENDIAN, ImConstant.MAX_FRAME_LENGTH, ImConstant.LENGTH_FIELD_OFFSET, ImConstant.LENGTH_FIELD_LENGTH, ImConstant.LENGTH_ADJUSTMENT, ImConstant.INITIAL_BYTES_TO_STRIP, ImConstant.FAIL_FAST))
                // 解码器
                .addLast(ImConstant.PACKET_CODEC, new PacketCodec())
                // packet 业务处理
                .addLast(ImConstant.PACKET_DISPATCHER_HANDLER, new PacketProtocolDispatcherHandler());

        // 移除协议分发器
        ctx.pipeline().remove(ImConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active，注意ctx.fireChannelActive 与ctx.pipeline().fireChannelActive 区别
        ctx.fireChannelActive();
    }
}
