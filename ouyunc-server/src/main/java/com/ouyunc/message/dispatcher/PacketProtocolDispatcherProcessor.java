package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.core.codec.PacketCodec;
import com.ouyunc.message.handler.PacketProtocolDispatcherHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteOrder;

/**
 * @author fzx
 * @description packet 协议,用于服务内部使用，主要用于集群节点之间通信
 */
public class PacketProtocolDispatcherProcessor implements ProtocolDispatcherProcessor<ByteBuf> {
    @Override
    public boolean match(ByteBuf in) {
        // 判断是何种协议,注意这里不可以使用  in.readByte();
        return isPacket(in.getByte(MessageConstant.ZERO));
    }

    @Override
    public void process(ChannelHandlerContext ctx) {
        ctx.pipeline()
                // 粘包半包处理
                .addLast(MessageConstant.PACKET_DECODE_HANDLER, new LengthFieldBasedFrameDecoder(ByteOrder.BIG_ENDIAN, MessageConstant.MAX_FRAME_LENGTH, MessageConstant.LENGTH_FIELD_OFFSET, MessageConstant.LENGTH_FIELD_LENGTH, MessageConstant.LENGTH_ADJUSTMENT, MessageConstant.INITIAL_BYTES_TO_STRIP, MessageConstant.FAIL_FAST))
                // 自定义编解码器
                .addLast(MessageConstant.PACKET_CODEC_HANDLER, new PacketCodec())
                // packet 协议分发处理器
                .addLast(MessageConstant.PACKET_DISPATCHER_HANDLER, new PacketProtocolDispatcherHandler());

        // 移除协议分发器
        ctx.pipeline().remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        // 调用下一个handle的active，注意ctx.fireChannelActive 与ctx.pipeline().fireChannelActive 区别
        ctx.fireChannelActive();
    }

    /**
     * @Author fzx
     * @Description 判断是否是packet类型协议
     */
    private boolean isPacket(byte magic1) {
        return magic1 == MessageConstant.PACKET_MAGIC;
    }
}
