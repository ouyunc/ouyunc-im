package com.ouyunc.message.convert;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;

/**
 * 外部入站 Metadata 白名单。
 * <p>必须在 {@code ClusterChannelGuard} 拒绝非法集群能力之后调用，避免先清掉
 * {@code clusterForwardMode} 等攻击标记而绕过现有拒绝逻辑。</p>
 * <p>集群 OUYUNC 连接保留完整内部 Metadata。</p>
 */
public final class ExternalIngressMetadata {

    private ExternalIngressMetadata() {
    }

    /**
     * Guard 通过后：外部连接只保留服务端在转换阶段写入的字段。
     */
    public static void retainTrustedAfterGuard(ChannelHandlerContext ctx, Packet packet) {
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
