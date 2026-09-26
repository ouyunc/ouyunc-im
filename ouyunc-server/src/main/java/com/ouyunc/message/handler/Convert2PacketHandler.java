package com.ouyunc.message.handler;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.PacketVerifier;
import com.ouyunc.message.cluster.auth.ClusterChannelGuard;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.convert.PacketConverter;
import com.ouyunc.message.protocol.NativePacketProtocol;
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
                if (ClusterChannelGuard.rejectExternalNativeCapability(ctx, packet)
                        || ClusterChannelGuard.rejectClientClusterCapability(ctx, packet)) {
                    return;
                }
                packetFormat(ctx, packet);
                ctx.fireChannelRead(packet);
                return;
            }
        }
        log.error("协议: {} 转换为packet发生异常,暂不支持该协议！", msg);
        throw new MessageException("协议转换为packet发生异常,暂不支持该协议！");
    }


    /**
     * 格式化packet, Guard 通过后：外部连接只保留服务端在转换阶段写入的字段。
     */
    private void packetFormat(ChannelHandlerContext ctx, Packet packet) {
        if (ctx == null || packet == null || packet.getMessage() == null) {
            return;
        }
        Protocol channelProtocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (channelProtocol == null
                || channelProtocol.getProtocol() == NativePacketProtocol.OUYUNC.getProtocol()) {
            return;
        }
        Message message = packet.getMessage();
        Metadata incoming = message.getMetadataOrNull();
        if (incoming != null && !incoming.isLocalIngress()) {
            // Guard 应已拒绝；不在此处清掉标记，避免掩盖漏检。
            return;
        }
        Metadata trusted = new Metadata();
        if (incoming != null) {
            trusted.getIngress().setAppKey(incoming.getIngress().getAppKey());
            trusted.getIngress().setClientIp(incoming.getIngress().getClientIp());
            trusted.getIngress().setOriginServerAddress(incoming.getIngress().getOriginServerAddress());
            trusted.getIngress().setServerTime(incoming.getIngress().getServerTime());
            trusted.getIngress().setIngressSource(incoming.getIngress().getIngressSource());
        }
        message.setMetadata(trusted);
    }

}
