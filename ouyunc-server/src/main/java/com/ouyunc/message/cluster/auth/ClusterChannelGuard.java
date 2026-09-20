package com.ouyunc.message.cluster.auth;

import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.ProtocolTypeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.model.Protocol;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按 Channel 判断这条连接能干什么，不按包头自报字段放行。
 * <p>外部连接禁止集群能力；集群连接须 HMAC。帧格式归 {@code PacketVerifier}，心跳字段归心跳入口。</p>
 */
public final class ClusterChannelGuard {

    private static final Logger log = LoggerFactory.getLogger(ClusterChannelGuard.class);

    private ClusterChannelGuard() {
    }

    /**
     * WS/HTTP/MQTT 连接上禁止集群 OUYUNC、客户端原生协议号冒充、集群转发包与集群消息类型。
     *
     * @return true 表示已拒绝并关闭连接
     */
    public static boolean rejectExternalInternalPacket(ChannelHandlerContext ctx, Packet packet) {
        Protocol channelProtocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (!isExternalClientProtocol(channelProtocol) || packet == null) {
            return false;
        }
        Metadata metadata = packet.getMessage() == null ? null : packet.getMessage().getMetadataOrNull();
        boolean clusterOrNativeClientProtocol = isClusterOrClientNativeProtocol(packet.getProtocol());
        boolean clusterForward = metadata != null && !metadata.isLocalIngress();
        boolean clusterType = isInternalClusterMessage(packet);
        if (!clusterOrNativeClientProtocol && !clusterForward && !clusterType) {
            return false;
        }
        log.warn("外部协议连接投递内部/原生能力包，关闭连接 remote={} channelProtocol={} packetProtocol={} forwardMode={} clusterType={}",
                ctx.channel().remoteAddress(),
                channelProtocol.getProtocol(),
                packet.getProtocol(),
                metadata == null ? null : metadata.getClusterForwardMode(),
                clusterType);
        ctx.close();
        return true;
    }

    /**
     * OUYUNC_CLIENT 通道：协议号必须与 Channel 一致，禁止集群转发 / 集群消息类型。
     *
     * @return true 表示已拒绝并关闭连接
     */
    public static boolean rejectClientClusterCapability(ChannelHandlerContext ctx, Packet packet) {
        Protocol channelProtocol = ctx.channel().attr(NativePacketProtocol.protocolAttrKey).get();
        if (channelProtocol == null
                || channelProtocol.getProtocol() != ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol()
                || packet == null) {
            return false;
        }
        Metadata metadata = packet.getMessage() == null ? null : packet.getMessage().getMetadataOrNull();
        boolean protocolMismatch = packet.getProtocol() != ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol()
                || packet.getProtocolVersion() != ProtocolTypeEnum.OUYUNC_CLIENT.getProtocolVersion();
        boolean clusterForward = metadata != null && !metadata.isLocalIngress();
        boolean clusterType = isInternalClusterMessage(packet);
        if (!protocolMismatch && !clusterForward && !clusterType) {
            return false;
        }
        log.warn("OUYUNC_CLIENT 连接使用非法能力，关闭连接 remote={} packetProtocol={} forwardMode={} clusterType={}",
                ctx.channel().remoteAddress(),
                packet.getProtocol(),
                metadata == null ? null : metadata.getClusterForwardMode(),
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
     * 集群转发包：对端须与 fromServerAddress 一致；落地必须是本机，中转目标必须仍有租约。
     * <p>peer 已由 Channel 认证确定；缺失或不一致的发送节点均拒绝。</p>
     */
    public static boolean allowClusterForward(String peer, Metadata metadata) {
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

    /**
     * 直连心跳：连接已认证，再核对本包 from/to/类型。不看 clusterForwardMode。
     * <p>from 必须是握手节点，to 必须是本机；只认 SYN/ACK 内容类型。</p>
     */
    public static boolean allowDirectHeartbeat(String peer, Packet packet) {
        if (StringUtils.isBlank(peer) || packet == null) {
            return false;
        }
        if (packet.getMessageType() != OuyuncMessageTypeEnum.SYN_ACK.getType()) {
            return false;
        }
        if (packet.getProtocol() != ProtocolTypeEnum.OUYUNC.getProtocol()
                || packet.getProtocolVersion() != ProtocolTypeEnum.OUYUNC.getProtocolVersion()) {
            log.warn("心跳协议与集群连接不一致 protocol={} version={}",
                    packet.getProtocol(), packet.getProtocolVersion());
            return false;
        }
        Message message = packet.getMessage();
        if (message == null) {
            return false;
        }
        int contentType = message.getContentType();
        if (contentType != OuyuncMessageContentTypeEnum.SYN_CONTENT.getType()
                && contentType != OuyuncMessageContentTypeEnum.ACK_CONTENT.getType()) {
            log.warn("心跳 contentType 非法 peer={} contentType={}", peer, contentType);
            return false;
        }
        if (!peer.equals(message.getFrom())) {
            log.warn("心跳 from 与握手身份不一致 peer={} from={}", peer, message.getFrom());
            return false;
        }
        String local = MessageServerContext.serverProperties().getLocalServerAddress();
        if (!local.equals(message.getTo())) {
            log.warn("心跳 to 不是本机 local={} to={}", local, message.getTo());
            return false;
        }
        return true;
    }

    /** 未转发的内部包只允许集群心跳/认证/取消重试，禁止走外部业务 Processor。 */
    public static boolean isInternalClusterMessage(Packet packet) {
        if (packet == null) {
            return false;
        }
        byte type = packet.getMessageType();
        return type == OuyuncMessageTypeEnum.SYN_ACK.getType()
                || type == OuyuncMessageTypeEnum.CLUSTER_AUTH.getType()
                || type == OuyuncMessageTypeEnum.QOS_RETRY_CANCEL.getType();
    }

    private static boolean isClusterOrClientNativeProtocol(byte protocol) {
        return protocol == ProtocolTypeEnum.OUYUNC.getProtocol()
                || protocol == ProtocolTypeEnum.OUYUNC_CLIENT.getProtocol();
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
