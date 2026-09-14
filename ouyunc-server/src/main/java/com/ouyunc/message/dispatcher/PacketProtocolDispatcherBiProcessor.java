package com.ouyunc.message.dispatcher;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.utils.PacketMagicUtil;
import com.ouyunc.message.cluster.auth.ClusterAuthentication;
import com.ouyunc.message.cluster.auth.ClusterAuthConstant;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.handler.PacketProtocolDispatcherHandler;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;

/**
 * @author fzx
 * @description packet 协议,用于服务内部使用，主要用于集群节点之间通信
 */
public class PacketProtocolDispatcherBiProcessor implements ProtocolDispatcherBiProcessor {
    private static final Logger log = LoggerFactory.getLogger(PacketProtocolDispatcherBiProcessor.class);

    @Override
    public boolean match(ByteBuf in) {
        // 判断是何种协议,注意这里不可以使用  in.readByte();
        byte[] magicBytes = new byte[MessageConstant.MAGIC_BYTE_LENGTH];
        in.getBytes(in.readerIndex(), magicBytes);
        return isPacket(magicBytes);
    }

    @Override
    public void process(ChannelHandlerContext ctx, ByteBuf in) {
        MessageServerProperties properties = MessageServerContext.serverProperties();
        // 单机或没有有效密钥时直接拒绝；新集群连接先 HMAC，再核对租约，最后才安装业务路由。
        if (properties == null || !properties.isClusterEnable()
                || !ClusterAuthentication.hasValidSecret(properties.getClusterSecret())) {
            log.warn("拒绝 OUYUNC 内部协议连接，集群未开启或认证密钥未配置，remote={}",
                    ctx.channel().remoteAddress());
            ctx.close();
            return;
        }
        ctx.pipeline()
                // 粘包半包处理
                .addLast(MessageConstant.PACKET_DECODE_HANDLER, new LengthFieldBasedFrameDecoder(ByteOrder.BIG_ENDIAN, MessageConstant.MAX_FRAME_LENGTH, MessageConstant.LENGTH_FIELD_OFFSET, MessageConstant.LENGTH_FIELD_LENGTH, MessageConstant.LENGTH_ADJUSTMENT, MessageConstant.INITIAL_BYTES_TO_STRIP, MessageConstant.FAIL_FAST))
                // 自定义编解码器
                .addLast(MessageConstant.PACKET_CODEC_HANDLER, new ClusterAuthentication.GuardedCodec())
                // 首包认证在协议分发之前，routed 标记和 SYN/ACK 均不能跳过认证。
                .addLast(ClusterAuthConstant.HANDLER_NAME,
                        new ClusterAuthentication.ServerHandler(properties.getClusterSecret(), properties.getLocalServerAddress()))
                // packet 协议分发处理器
                .addLast(MessageConstant.PACKET_DISPATCHER_HANDLER, new PacketProtocolDispatcherHandler());

        // 移除协议分发器
        ctx.pipeline().remove(MessageConstant.PROTOCOL_DISPATCHER_HANDLER);
        // 调用下一个handle
        ctx.fireChannelRead(in.retain());
    }

    /**
     * @Author fzx
     * @Description 判断是否是packet类型协议
     */
    private boolean isPacket(byte[] magicBytes) {
        return PacketMagicUtil.isPacketMagic(magicBytes);
    }
}
