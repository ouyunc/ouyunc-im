package com.ouyunc.message.cluster.auth;

import com.ouyunc.base.constant.MessageConstant;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;

/**
 * 集群入站粘包解码：长度域可读时就按认证状态限制帧长。
 * 未认证最多 {@link ClusterAuthConstant#MAX_AUTH_FRAME_BYTES}；认证后再允许正常业务帧。
 */
public final class ClusterAuthFrameDecoder extends LengthFieldBasedFrameDecoder {
    private static final Logger log = LoggerFactory.getLogger(ClusterAuthFrameDecoder.class);

    public ClusterAuthFrameDecoder() {
        super(ByteOrder.BIG_ENDIAN,
                MessageConstant.MAX_FRAME_LENGTH,
                MessageConstant.LENGTH_FIELD_OFFSET,
                MessageConstant.LENGTH_FIELD_LENGTH,
                MessageConstant.LENGTH_ADJUSTMENT,
                MessageConstant.INITIAL_BYTES_TO_STRIP,
                MessageConstant.FAIL_FAST);
    }

    @Override
    protected Object decode(ChannelHandlerContext ctx, ByteBuf in) throws Exception {
        int maxFrameBytes = ctx.channel().attr(ClusterAuthConstant.AUTHENTICATED_NODE).get() == null
                ? ClusterAuthConstant.MAX_AUTH_FRAME_BYTES
                : MessageConstant.MAX_FRAME_LENGTH;
        if (in.readableBytes() >= MessageConstant.LENGTH_FIELD_OFFSET + MessageConstant.LENGTH_FIELD_LENGTH) {
            int bodyLength = in.getInt(in.readerIndex() + MessageConstant.LENGTH_FIELD_OFFSET);
            long frameLength = (long) MessageConstant.PACKET_BASE_LENGTH + (long) bodyLength;
            if (bodyLength < 0 || frameLength > maxFrameBytes) {
                log.warn("拒绝超限集群帧 remote={} authenticated={} bodyLength={} maxFrame={}",
                        ctx.channel().remoteAddress(),
                        ctx.channel().attr(ClusterAuthConstant.AUTHENTICATED_NODE).get() != null,
                        bodyLength,
                        maxFrameBytes);
                in.skipBytes(in.readableBytes());
                ctx.close();
                return null;
            }
        }
        return super.decode(ctx, in);
    }
}
