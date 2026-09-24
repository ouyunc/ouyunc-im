package com.ouyunc.message.handler;

import com.ouyunc.base.constant.enums.ClusterForwardModeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
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
 * 已认证集群连接上的用途分流：心跳 / INTERNAL 控制包 / CLIENT 落地。
 * <p>连接身份由 Guard 提供；不在这里做帧结构校验。</p>
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
        Metadata metadata = packet.getMessage().getMetadataOrNull();
        ClusterForwardModeEnum mode = metadata == null
                ? ClusterForwardModeEnum.NONE
                : metadata.clusterForwardModeOrNone();
        if (mode == ClusterForwardModeEnum.NONE) {
            dispatchLocalClusterPacket(ctx, packet, peer);
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
        Target target = metadata.getClusterRoute().getTarget();
        String localServerAddress = MessageServerContext.serverProperties().getLocalServerAddress();
        if (CollectionUtils.isNotEmpty(metadata.getClusterRoute().getFanoutTargets())
                && target != null
                && Objects.equals(localServerAddress, target.getTargetServerAddress())) {
            ClientHelper.deliverLocalFanoutTargets(packet, metadata.getClusterRoute().getFanoutTargets());
            return;
        }
        if (metadata.getClusterRoute().isLocalBroadcastOnly() && target != null
                && Objects.equals(localServerAddress, target.getTargetServerAddress())) {
            ClientHelper.deliverLocalBroadcast(metadata.getIngress().getAppKey(), packet);
            return;
        }
        MessageHelper.asyncSendMessageWithoutInterceptor(packet, target);
    }

    /**
     * 未转发包：心跳绑定已认证对端；QOS_RETRY_CANCEL 必须走 INTERNAL；认证首包不应到这里。
     */
    private static void dispatchLocalClusterPacket(ChannelHandlerContext ctx, Packet packet, String peer) {
        byte type = packet.getMessageType();
        if (type == OuyuncMessageTypeEnum.CLUSTER_AUTH.getType()) {
            log.warn("忽略已过认证阶段的 CLUSTER_AUTH packetId={}", packet.getPacketId());
            return;
        }
        if (type == OuyuncMessageTypeEnum.SYN_ACK.getType()) {
            if (!ClusterChannelGuard.allowDirectHeartbeat(peer, packet)) {
                log.warn("拒绝非法集群心跳 packetId={} peer={}", packet.getPacketId(), peer);
                return;
            }
            ctx.fireChannelRead(packet);
            return;
        }
        log.warn("拒绝未转发的非集群消息 packetId={} type={}", packet.getPacketId(), type);
    }
}
