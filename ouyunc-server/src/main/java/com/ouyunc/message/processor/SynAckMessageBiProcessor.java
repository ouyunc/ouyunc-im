package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.cluster.auth.ClusterChannelGuard;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

/**
 * 集群直连心跳：SYN 回 ACK，ACK 清 miss 计数。字段校验绑定已认证对端，不依赖 metadata。
 */
public final class SynAckMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(SynAckMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return OuyuncMessageTypeEnum.SYN_ACK;
    }

    @Override
    public Mono<Void> process(ChannelHandlerContext ctx, Packet packet) {
        return Mono.fromRunnable(() -> {
            String peer = ClusterChannelGuard.requireAuthenticatedPeer(ctx);
            if (peer == null || !ClusterChannelGuard.allowDirectHeartbeat(peer, packet)) {
                log.warn("丢弃未通过心跳校验的 SYN/ACK packetId={}",
                        packet == null ? null : packet.getPacketId());
                return;
            }
            Message synAckMessage = packet.getMessage();
            int contentType = synAckMessage.getContentType();
            String remoteServerAddress = synAckMessage.getFrom();
            if (OuyuncMessageContentTypeEnum.SYN_CONTENT.getType() == contentType) {
                replyAck(packet, synAckMessage, remoteServerAddress);
                discoverPeerIfNeeded(remoteServerAddress);
                return;
            }
            if (OuyuncMessageContentTypeEnum.ACK_CONTENT.getType() == contentType) {
                MessageClientPool.markHealthy(remoteServerAddress);
                discoverPeerIfNeeded(remoteServerAddress);
            }
        });
    }

    private static void replyAck(Packet packet, Message synAckMessage, String remoteServerAddress) {
        synAckMessage.setId(MessageContext.idGenerator().generateIdStr());
        synAckMessage.setContentType(OuyuncMessageContentTypeEnum.ACK_CONTENT.getType());
        synAckMessage.setFrom(MessageServerContext.serverProperties().getLocalServerAddress());
        synAckMessage.setTo(remoteServerAddress);
        synAckMessage.setCreateTime(TimeUtil.currentTimeMillis());
        packet.setPacketId(MessageContext.idGenerator().generateId());
        MessageServerContext.findProtocol(packet.getProtocol(), packet.getProtocolVersion())
                .doSendMessage(packet, remoteServerAddress, sendResult -> {});
    }

    private static void discoverPeerIfNeeded(String remoteServerAddress) {
        if (MessageServerContext.clusterActiveServerRegistryTableCache.get(remoteServerAddress) != null
                || MessageServerContext.clusterGlobalServerRegistryTableCache.get(remoteServerAddress) != null) {
            return;
        }
        if (NodeLeaseKeeper.hasLiveLease(remoteServerAddress)) {
            ThreadPoolManager.messageProcessorExecutor().submit(() ->
                    MessageClientPool.ensurePool(remoteServerAddress));
        }
    }
}
