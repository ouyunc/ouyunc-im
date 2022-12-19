package com.ouyunc.im.dispatcher;

import com.ouyunc.im.codec.PacketCodec;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.handler.PacketProtocolDispatcherHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import io.netty.handler.logging.LogLevel;
import io.netty.handler.logging.LoggingHandler;

import java.nio.ByteOrder;

/**
 * @Author fangzhenxun
 * @Description: packet 调度处理器
 * @Version V3.0
 **/
public class PacketDispatcherProcessorStrategy implements DispatcherProcessorStrategy{


    @Override
    public void doProcess(ChannelHandlerContext ctx) {
        ctx.pipeline()
                // 日志
                .addLast(IMConstant.LOG, new LoggingHandler(IMServerContext.SERVER_CONFIG.getLogLevel()))
                // 粘包半包处理
                .addLast(IMConstant.PACKET_DECODE, new LengthFieldBasedFrameDecoder(ByteOrder.BIG_ENDIAN, IMConstant.MAX_FRAME_LENGTH, IMConstant.LENGTH_FIELD_OFFSET, IMConstant.LENGTH_FIELD_LENGTH, IMConstant.LENGTH_ADJUSTMENT, IMConstant.INITIAL_BYTES_TO_STRIP, IMConstant.FAIL_FAST))
                // 自定义编解码器
                .addLast(IMConstant.PACKET_CODEC, new PacketCodec())
                // packet 协议分发处理器
                .addLast(IMConstant.PACKET_DISPATCHER_HANDLER, new PacketProtocolDispatcherHandler());

        // 移除协议分发器
        ctx.pipeline().remove(IMConstant.PROTOCOL_DISPATCHER);
        // 调用下一个handle的active，注意ctx.fireChannelActive 与ctx.pipeline().fireChannelActive 区别
        ctx.fireChannelActive();
    }
}
