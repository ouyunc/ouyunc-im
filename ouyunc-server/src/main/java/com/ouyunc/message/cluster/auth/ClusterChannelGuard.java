package com.ouyunc.message.cluster.auth;

import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 内部协议入口的轻量校验：HMAC 之后只认已认证 Channel，外部协议不允许内部包。
 */
public final class ClusterChannelGuard {

    private static final Logger log = LoggerFactory.getLogger(ClusterChannelGuard.class);

    private ClusterChannelGuard() {
    }

    /**
     * WS/HTTP/MQTT 连接上禁止 OUYUNC 协议和 routed 包，避免外部入口冒充集群路由。
     *
     * @return true 表示已拒绝并关闭连接
     */
    public static boolean rejectExternalInternalPacket(ChannelHandlerContext ctx, Packet packet) {
        Protocol channelProtocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (!isExternalClientProtocol(channelProtocol) || packet == null) {
            return false;
        }
        Metadata metadata = packet.getMessage() == null ? null : packet.getMessage().getMetadata();
        boolean internalProtocol = packet.getProtocol() == ProtocolTypeEnum.OUYUNC.getProtocol();
        boolean routed = metadata != null && metadata.isRouted();
        boolean clusterType = isInternalClusterMessage(packet);
        if (!internalProtocol && !routed && !clusterType) {
            return false;
        }
        log.warn("外部协议连接投递内部包，关闭连接 remote={} channelProtocol={} packetProtocol={} routed={} clusterType={}",
                ctx.channel().remoteAddress(),
                channelProtocol.getProtocol(),
                packet.getProtocol(),
                routed,
                clusterType);
        ctx.close();
        return true;
    }

    /**
     * 集群入站必须先完成 HMAC 握手。
     *
     * @return 对端节点 id；未认证时关闭连接并返回 null
     */
    public static String requireAuthenticatedPeer(ChannelHandlerContext ctx) {
        String peer = ctx.channel().attr(ClusterAuthConstant.AUTHENTICATED_NODE).get();
        if (StringUtils.isNotBlank(peer)) {
            return peer;
        }
        log.warn("拒绝未认证的 OUYUNC 业务包 remote={}", ctx.channel().remoteAddress());
        ctx.close();
        return null;
    }

    /**
     * routed 包：对端须与 fromServerAddress 一致；落地必须是本机，中转目标必须仍有租约。
     * <p>peer 已由 Channel 认证确定；缺失或不一致的发送节点均拒绝。</p>
     */
    public static boolean allowRoutedDelivery(String peer, Metadata metadata) {
        if (metadata == null) {
            return false;
        }
        // MessageHelper 经集群连接写出前会写本机地址；空值不得放行。
        if (!peer.equals(metadata.getFromServerAddress())) {
            log.warn("集群路由包发送节点与握手身份不一致 peer={} fromServer={}",
                    peer, metadata.getFromServerAddress());
            return false;
        }
        Target target = metadata.getTarget();
        if (target == null || StringUtils.isBlank(target.getTargetServerAddress())) {
            return false;
        }
        String dest = target.getTargetServerAddress();
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (dest.equals(local)) {
            return true;
        }
        if (NodeLeaseKeeper.hasLiveLease(dest)) {
            return true;
        }
        log.warn("集群路由目标不在本机且无活租约 dest={}", dest);
        return false;
    }

    /** 未路由的内部包只允许集群心跳/关系失效，禁止走外部业务 Processor。 */
    public static boolean isInternalClusterMessage(Packet packet) {
        if (packet == null) {
            return false;
        }
        byte type = packet.getMessageType();
        return type == OuyuncMessageTypeEnum.SYN_ACK.getType()
                || type == OuyuncMessageTypeEnum.RELATION_CACHE_INVALIDATE.getType();
    }

    private static boolean isExternalClientProtocol(Protocol protocol) {
        if (protocol == null) {
            return false;
        }
        byte value = protocol.getProtocol();
        return value == ProtocolTypeEnum.WS.getProtocol()
                || value == ProtocolTypeEnum.HTTP.getProtocol()
                || value == ProtocolTypeEnum.MQTT.getProtocol();
    }
}
