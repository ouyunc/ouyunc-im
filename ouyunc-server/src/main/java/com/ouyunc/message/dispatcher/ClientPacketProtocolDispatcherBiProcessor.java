package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.utils.PacketMagicUtil;
import com.ouyunc.core.codec.PacketCodec;
import com.ouyunc.message.handler.ClientPacketProtocolDispatcherHandler;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;

/**
 * 客户端原生 Packet（protocol=OUYUNC_CLIENT）：复用 Packet 编解码，不走集群 HMAC/路由。
 * <p>与 {@link PacketProtocolDispatcherBiProcessor} 共用魔数，靠首包协议号分流。</p>
 */
public class ClientPacketProtocolDispatcherBiProcessor implements ProtocolDispatcherBiProcessor {
    private static final Logger log = LoggerFactory.getLogger(ClientPacketProtocolDispatcherBiProcessor.class);

    @Override
    public boolean match(ByteBuf in) {
        return PacketMagicUtil.matchesPacketProtocol(in, ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol());
    }

    @Override
    public void process(ChannelHandlerContext ctx, ByteBuf in) {
        log.debug("接受 OUYUNC_CLIENT 连接，remote={}", ctx.channel().remoteAddress());
        ctx.pipeline()
                .addLast(MessageConstant.PACKET_DECODE_HANDLER, new LengthFieldBasedFrameDecoder(
                        ByteOrder.BIG_ENDIAN,
                        MessageConstant.MAX_FRAME_LENGTH,
                        MessageConstant.LENGTH_FIELD_OFFSET,
                        MessageConstant.LENGTH_FIELD_LENGTH,
                        MessageConstant.LENGTH_ADJUSTMENT,
                        MessageConstant.INITIAL_BYTES_TO_STRIP,
                        MessageConstant.FAIL_FAST))
                // 普通 PacketCodec，无集群认证 Guard
                .addLast(MessageConstant.PACKET_CODEC_HANDLER, new PacketCodec())
                .addLast(MessageConstant.PACKET_DISPATCHER_HANDLER, new ClientPacketProtocolDispatcherHandler());

        ctx.pipeline().remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        ctx.fireChannelRead(in.retain());
    }
}
