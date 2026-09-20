package com.ouyunc.core.codec;


import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.exception.OutboundPacketVerifyException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.PacketReaderWriterUtil;
import com.ouyunc.base.utils.PacketVerifier;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.ByteToMessageCodec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Packet 编解码。这里只做帧结构校验。
 * <p>入站非法：对端坏数据，关连接。出站非法：本机构包错误，失败本次发送，不关连接。</p>
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
        if (!verify(packet, ctx)) {
            log.error("本节点出站协议包校验失败, 本次发送失败且不关闭连接, packetId={}",
                    packet == null ? null : packet.getPacketId());
            throw new OutboundPacketVerifyException("本节点构造的协议包校验失败");
        }
        PacketReaderWriterUtil.writePacketInByteBuf(packet, out);
    }



    /**
     * 帧结构校验，不含连接能力或业务语义。
     */
    protected boolean verify(Packet packet, ChannelHandlerContext ctx) {
        return PacketVerifier.verify(packet);
    }

    /**
     * 解码：完整包才消费。半包直接 return，由 {@code ByteToMessageDecoder} 累计，禁止抛错关连。
     * 有前置 LengthFieldBasedFrameDecoder 时这里是双保险。
     */
    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf in, List<Object> out) throws Exception {
        if (!hasCompletePacket(in)) {
            return;
        }
        Packet packet = PacketReaderWriterUtil.readByteBuf2Packet(in);
        if (!verify(packet, ctx)) {
            log.error("协议包:{} 解码后校验失败,开始关闭通道channel.", packet);
            ctx.close();
            return;
        }
        out.add(packet);
    }

    /**
     * 可读字节不够一帧则等待；非法长度交给 {@code readByteBuf2Packet} 拒绝。
     */
    private static boolean hasCompletePacket(ByteBuf in) {
        if (in.readableBytes() < MessageConstant.PACKET_BASE_LENGTH) {
            return false;
        }
        int messageLength = in.getInt(in.readerIndex() + MessageConstant.LENGTH_FIELD_OFFSET);
        if (messageLength < 0 || messageLength > MessageConstant.MAX_MESSAGE_CONTENT_LENGTH) {
            return true;
        }
        return in.readableBytes() >= MessageConstant.PACKET_BASE_LENGTH + messageLength;
    }

}
