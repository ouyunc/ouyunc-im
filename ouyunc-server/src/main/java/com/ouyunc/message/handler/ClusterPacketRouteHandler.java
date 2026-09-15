package com.ouyunc.message.handler;

import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.cluster.auth.ClusterChannelGuard;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 集群中如果对方客户端不在同一台 server 中需要将消息路由投递到登录的服务中。
 * <p>{@code routed=true} 时按 {@link Target#getTargetServerAddress()} 继续投递；该地址是最终落地机，中间节点不得改写。
 */
public class ClusterPacketRouteHandler extends SimpleChannelInboundHandler<Packet> {
    private static final Logger log = LoggerFactory.getLogger(ClusterPacketRouteHandler.class);

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, Packet packet) throws Exception {
        String peer = ClusterChannelGuard.requireAuthenticatedPeer(ctx);
        if (peer == null) {
            return;
        }
        if (packet.getMessage() == null) {
            log.warn("集群包缺少 message, packetId={}", packet.getPacketId());
            return;
        }
        Metadata metadata = packet.getMessage().getMetadata();
        if (metadata == null || !metadata.isRouted()) {
            if (!ClusterChannelGuard.isInternalClusterMessage(packet)) {
                log.warn("拒绝未路由的非集群消息 packetId={} type={}", packet.getPacketId(), packet.getMessageType());
                return;
            }
            ctx.fireChannelRead(packet);
            return;
        }
        if (!ClusterChannelGuard.allowRoutedDelivery(peer, metadata)) {
            log.warn("拒绝非法集群路由包 packetId={} peer={}", packet.getPacketId(), peer);
            return;
        }
        Target target = metadata.getTarget();
        String localServerAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        if (CollectionUtils.isNotEmpty(metadata.getFanoutTargets())
                && target != null
                && Objects.equals(localServerAddress, target.getTargetServerAddress())) {
            ClientHelper.deliverLocalFanoutTargets(packet, metadata.getFanoutTargets());
            return;
        }
        if (metadata.isLocalBroadcastOnly() && target != null
                && Objects.equals(localServerAddress, target.getTargetServerAddress())) {
            ClientHelper.deliverLocalBroadcast(metadata.getAppKey(), packet);
            return;
        }
        MessageHelper.asyncSendMessageWithoutInterceptor(packet, target);
    }
}
