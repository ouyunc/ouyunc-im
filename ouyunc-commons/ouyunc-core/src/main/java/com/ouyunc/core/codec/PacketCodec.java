package com.ouyunc.core.codec;


import com.ouyunc.base.utils.PacketReaderWriterUtil;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * @Author fzx
 * @Description: 自定义编解码器，消息在服务器内部都会转成该packet格式的消息进行传递和处理
 **/
public class PacketCodec extends ByteToMessageCodec<Packet> {
    private static final Logger log = LoggerFactory.getLogger(PacketCodec.class);



    /**
     * @Author fzx
     * @Description 编码
     * @param ctx
     * @param packet
     * @param out
     * @return void
     */
    @Override
    protected void encode(ChannelHandlerContext ctx, Packet packet, ByteBuf out) throws Exception {
        // 自定义
        if (!verify(packet, ctx)) {
            log.error("协议包:{} 校验失败,开始关闭通道channel.", packet);
            ctx.close();
            throw new MessageException("协议包校验失败!");
        }
        PacketReaderWriterUtil.writePacketInByteBuf(packet, out);
    }



    /**
     * @Author fzx
     * @Description 校验协议包字段参数,可以调用校验器来校验 @todo 待完善
     */
    protected boolean verify(Packet packet, ChannelHandlerContext ctx) {
        // 校验魔数
        // 校验协议包id
        // 校验设备类型
        // 校验消息类型
        // 校验加密方式
        // 校验序列化方式
        // 校验权限
        return true;
    }

    /**
     * @Author fzx
     * @Description 解码，需要处理半包粘包,如果长时间不out出去，则会消息堆积
     * @param ctx
     * @param in
     * @param out
     * @return void
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        out.add(PacketReaderWriterUtil.readByteBuf2Packet(in));
    }

}
