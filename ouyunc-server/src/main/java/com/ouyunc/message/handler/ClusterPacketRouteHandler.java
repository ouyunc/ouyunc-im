package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
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
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;

/**
 * 集群入站分流：{@link ClusterForwardModeEnum#CLIENT} 写客户端；
 * {@link ClusterForwardModeEnum#INTERNAL} 到目标节点后进 Processor，不写客户端。
 * {@code Target.targetServerAddress} 是最终节点，中间跳不得改写。
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
        ClusterForwardModeEnum mode = metadata == null
                ? ClusterForwardModeEnum.NONE
                : metadata.clusterForwardModeOrNone();
        if (mode == ClusterForwardModeEnum.NONE) {
            if (!ClusterChannelGuard.isInternalClusterMessage(packet)) {
                log.warn("拒绝未转发的非集群消息 packetId={} type={}", packet.getPacketId(), packet.getMessageType());
                return;
            }
            ctx.fireChannelRead(packet);
            return;
        }
        if (!ClusterChannelGuard.allowClusterForward(peer, metadata)) {
            log.warn("拒绝非法集群转发包 packetId={} peer={}", packet.getPacketId(), peer);
            return;
        }
        if (mode == ClusterForwardModeEnum.INTERNAL) {
            handleInternalForward(ctx, packet);
            return;
        }
        handleClientForward(packet, metadata);
    }

    /** 内部控制包：本机是最终节点则进 Processor，否则继续发往 dest。 */
    private static void handleInternalForward(ChannelHandlerContext ctx, Packet packet) {
        String dest = MessageHelper.clusterDest(packet);
        if (StringUtils.isBlank(dest)) {
            log.warn("集群内部控制包缺少 Target.targetServerAddress packetId={}", packet.getPacketId());
            return;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (!dest.equals(local)) {
            MessageHelper.sendClusterInternal(packet, dest);
            return;
        }
        ctx.fireChannelRead(packet);
    }

    private static void handleClientForward(Packet packet, Metadata metadata) {
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
