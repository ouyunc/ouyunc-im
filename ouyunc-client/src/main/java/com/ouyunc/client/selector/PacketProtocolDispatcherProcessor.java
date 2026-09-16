package com.ouyunc.client.selector;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.core.codec.PacketCodec;
import io.netty.channel.Channel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

import java.nio.ByteOrder;

/**
 * 客户端原生 Packet / 集群 Packet：LengthField 拆包 + PacketCodec。
 */
public class PacketProtocolDispatcherProcessor implements ProtocolSelector<Protocol, Channel> {

    @Override
    public boolean match(Protocol protocol) {
        if (protocol == null) {
            return false;
        }
        byte type = protocol.getProtocol();
        return type == ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol()
                || type == ProtocolTypeEnum.OUYUNC.getProtocol();
    }

    @Override
    public void process(Channel channel) {
        channel.pipeline()
                .addLast(MessageConstant.PACKET_DECODE_HANDLER, new LengthFieldBasedFrameDecoder(
                        ByteOrder.BIG_ENDIAN,
                        MessageConstant.MAX_FRAME_LENGTH,
                        MessageConstant.LENGTH_FIELD_OFFSET,
                        MessageConstant.LENGTH_FIELD_LENGTH,
                        MessageConstant.LENGTH_ADJUSTMENT,
                        MessageConstant.INITIAL_BYTES_TO_STRIP,
                        MessageConstant.FAIL_FAST))
                .addLast(MessageConstant.PACKET_CODEC_HANDLER, new PacketCodec());
    }
}
