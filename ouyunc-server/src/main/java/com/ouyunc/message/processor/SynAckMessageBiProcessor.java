package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: im内部客户端使用的心跳消息syn-ack处理器
 **/
public final class SynAckMessageBiProcessor extends AbstractMessageBiProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(SynAckMessageBiProcessor.class);

    @Override
    public MessageType type() {
        return OuyuncMessageTypeEnum.SYN_ACK;
    }

    /**
     * 内部客户端 syn-ack的逻辑处理, 注意：记住from 和 to 代表的什么含义以及所存储的值是什么
     */
    @Override
    public void process(ChannelHandlerContext ctx, Packet packet) {
        // 判断收到的是syn还是ack
        // 如果是syn 则发送ack,如果是ack，则注册表增加服务
        Message synAckMessage = packet.getMessage();
        // 需要判断收到的消息是目的地是否是本服务器，如果不是在次将消息包传递出去，如果是则处理
        int contentType = synAckMessage.getContentType();
        // 发送端服务器地址：ip:port
        String remoteServerAddress = synAckMessage.getFrom();
        // syn 可能是经过其他服务转发的，回去的ack可能是经过其他服务转发的
        if (OuyuncMessageContentTypeEnum.SYN_CONTENT.getType() == contentType) {
            synAckMessage.setId(MessageContext.idGenerator().generateIdStr());
            synAckMessage.setContentType(OuyuncMessageContentTypeEnum.ACK_CONTENT.getType());
            synAckMessage.setFrom(MessageServerContext.serverProperties().getLocalServerAddress());
            synAckMessage.setTo(remoteServerAddress);
            synAckMessage.setCreateTime(TimeUtil.currentTimeMillis());
            packet.setPacketId(MessageContext.idGenerator().generateId());
            MessageServerContext.findProtocol(packet.getProtocol(), packet.getProtocolVersion()).doSendMessage(packet, remoteServerAddress, (sendResult)->{});
            // 下面是解决集群中原有服务是如何发现新加入集群的服务的
            // 判断发到syn的服务是否在 全局服务注册表中，如果不在判断该服务的合法性，如果合法，尝试发送给对方syn进行探测，如果成功则将新加入集群中的服务添加到激活的路由表中
            if (MessageServerContext.clusterActiveServerRegistryTableCache.get(remoteServerAddress) == null && MessageServerContext.clusterGlobalServerRegistryTableCache.get(remoteServerAddress) == null) {
                if (NodeLeaseKeeper.hasLiveLease(remoteServerAddress)) {
                    ThreadPoolManager.messageProcessorExecutor().submit(() ->
                            MessageClientPool.ensurePool(remoteServerAddress));
                }
            }
        } else if (OuyuncMessageContentTypeEnum.ACK_CONTENT.getType() == contentType) {
            MessageServerContext.clusterClientMissAckTimesCache.delete(remoteServerAddress);
            // 只把租约仍活的节点放回可投递池；get(poolMap) 会无脑建连，不能当发现手段
            if (NodeLeaseKeeper.hasLiveLease(remoteServerAddress)) {
                MessageClientPool.ensurePool(remoteServerAddress);
            }
        }
    }


}
