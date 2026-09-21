package com.ouyunc.message.handler;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.utils.PacketVerifier;
import com.ouyunc.message.cluster.auth.ClusterChannelGuard;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.ExternalIngressMetadata;
import com.ouyunc.message.convert.PacketConverter;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 入站转成 Packet 后先做帧结构检查，再按 Channel 拦集群能力；
 * Guard 通过后外部连接才换成白名单 Metadata。
 */
public class Convert2PacketHandler extends SimpleChannelInboundHandler<Object> {
    private static final Logger log = LoggerFactory.getLogger(Convert2PacketHandler.class);

    /**
     * @param ctx
     * @param msg
     * @return void
     * @Author fzx
     * @Description 类型转换
     */
    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
        for (PacketConverter<?> packetConverter : MessageServerContext.packetConverterList) {
            Packet packet = packetConverter.convertToPacket(ctx, msg);
            if (packet != null) {
                if (!PacketVerifier.verify(packet)) {
                    log.error("入站转换后帧结构非法, 关闭 channelId={}", ctx.channel().id().asShortText());
                    ctx.close();
                    return;
                }
                if (ClusterChannelGuard.rejectExternalInternalPacket(ctx, packet)
                        || ClusterChannelGuard.rejectClientClusterCapability(ctx, packet)) {
                    return;
                }
                ExternalIngressMetadata.retainTrustedAfterGuard(ctx, packet);
                ctx.fireChannelRead(packet);
                return;
            }
        }
        log.error("协议: {} 转换为packet发生异常,暂不支持该协议！", msg);
        throw new MessageException("协议转换为packet发生异常,暂不支持该协议！");
    }



}
