package com.ouyunc.message.thread;

import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.exception.OutboundPacketVerifyException;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.MapUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 集群间节点心跳：只探已有连接池健康，不再用半数节点自杀。
 * <p>missAck 只统计「已发出或网络失败」；本地构包失败单独记日志，不当成对端无应答。</p>
 */
public class MessageClusterSynAckThread implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(MessageClusterSynAckThread.class);

    @Override
    public void run() {
        log.debug("集群服务中当前存活的服务：{}", MessageServerContext.clusterActiveServerRegistryTableCache.asMap().keySet());
        Set<Map.Entry<String, ChannelPool>> availableGlobalServer = MapUtil.mergerMaps(
                MessageServerContext.clusterActiveServerRegistryTableCache.asMap(),
                MessageServerContext.clusterGlobalServerRegistryTableCache.asMap()).entrySet();
        for (Map.Entry<String, ChannelPool> socketAddressChannelPoolEntry : availableGlobalServer) {
            String targetServerAddress = socketAddressChannelPoolEntry.getKey();
            if (!NodeLeaseKeeper.hasLiveLease(targetServerAddress)) {
                continue;
            }
            Message message = new Message(MessageContext.idGenerator().generateIdStr(),
                    MessageServerContext.serverProperties().getLocalServerAddress(),
                    targetServerAddress,
                    OuyuncMessageContentTypeEnum.SYN_CONTENT.getType(),
                    TimeUtil.currentTimeMillis());
            Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(),
                    NativePacketProtocol.OUYUNC.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(),
                    DeviceTypeEnum.PC.getType(),
                    NetworkEnum.OTHER.getValue(),
                    Encrypt.SymmetryEncrypt.NONE.getValue(),
                    Serializer.PROTO_STUFF.getValue(),
                    OuyuncMessageTypeEnum.SYN_ACK.getType(),
                    message);
            MessageServerContext.findProtocol(packet.getProtocol(), packet.getProtocolVersion())
                    .doSendMessage(packet, targetServerAddress,
                            sendResult -> onSynSendResult(targetServerAddress, sendResult));
        }
    }

    /**
     * 已发出或网络失败才计入等待 ACK；本地校验失败不摘池。
     */
    static void onSynSendResult(String targetServerAddress, SendResult sendResult) {
        if (sendResult != null
                && OutboundPacketVerifyException.isLocalVerifyFailure(sendResult.getException())) {
            log.error("集群心跳本地构包失败 dest={} packetId={}",
                    targetServerAddress,
                    sendResult.getPacket() == null ? null : sendResult.getPacket().getPacketId());
            return;
        }
        if (sendResult != null && sendResult.getSendStatus() != SendStatusEnum.SEND_OK) {
            log.warn("集群心跳发送失败 dest={} cause={}",
                    targetServerAddress,
                    sendResult.getException() == null ? null : sendResult.getException().getMessage());
        }
        noteWaitingAck(targetServerAddress);
    }

    private static void noteWaitingAck(String targetServerAddress) {
        if (!MessageServerContext.clusterActiveServerRegistryTableCache.asMap().containsKey(targetServerAddress)) {
            return;
        }
        AtomicInteger missAckTimes = MessageServerContext.clusterClientMissAckTimesCache.get(targetServerAddress);
        if (missAckTimes.incrementAndGet() > MessageServerContext.serverProperties().getClusterClientHeartbeatWaitRetry()) {
            MessageServerContext.clusterActiveServerRegistryTableCache.delete(targetServerAddress);
            log.warn("集群节点 SYN 连续无 ACK，从可投递池摘除（租约仍在则继续重试）: {}", targetServerAddress);
        }
    }
}
