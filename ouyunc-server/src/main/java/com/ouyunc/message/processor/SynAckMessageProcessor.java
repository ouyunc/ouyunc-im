package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.MessageType;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;

/**
 * @Author fzx
 * @Description: im内部客户端使用的心跳消息syn-ack处理器
 **/
public class SynAckMessageProcessor extends AbstractMessageProcessor<Byte> {
    private static final Logger log = LoggerFactory.getLogger(SynAckMessageProcessor.class);

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
        log.info("SynAckMessageProcessor 集群模式下,远端Message服务：remoteServerAddress = {} 正在发起的contentType = {} 请求", remoteServerAddress, contentType);
        // syn 可能是经过其他服务转发的，回去的ack可能是经过其他服务转发的
        if (OuyuncMessageContentTypeEnum.SYN_CONTENT.getType() == contentType) {
            synAckMessage.setContentType(OuyuncMessageContentTypeEnum.ACK_CONTENT.getType());
            synAckMessage.setFrom(MessageServerContext.serverProperties().getLocalServerAddress());
            synAckMessage.setTo(remoteServerAddress);
            synAckMessage.setCreateTime(Clock.systemUTC().millis());
            packet.setPacketId(MessageServerContext.<Long>idGenerator().generateId());
            Target target = Target.newBuilder().targetIdentity(remoteServerAddress).build();
            MessageHelper.syncSendMessage(packet, target);
            // 下面是解决集群中原有服务是如何发现新加入集群的服务的
            // 判断发到syn的服务是否在 全局服务注册表中，如果不在判断该服务的合法性，如果合法，尝试发送给对方syn进行探测，如果成功则将新加入集群中的服务添加到激活的路由表中
            if (MessageServerContext.clusterActiveServerRegistryTableCache.get(remoteServerAddress) == null && MessageServerContext.clusterGlobalServerRegistryTableCache.get(remoteServerAddress) == null) {
                // 1,不在全局服务注册表中
                // 2,判断服务的合法性， 这里不做太多的验证，认为只要加入集群中的服务都是合法的，如果后期需要，在加入校验规则
                // 3,给对方发送syn心跳，去探测是否连通
                synAckMessage.setContentType(OuyuncMessageContentTypeEnum.SYN_CONTENT.getType());
                packet.setPacketId(MessageServerContext.<Long>idGenerator().generateId());
                // 内部客户端连接池异步传递消息 syn ,尝试所有的路径去保持连通
                MessageHelper.asyncSendMessage(packet, target);
            }
        }
        // 收到回应则添加新的服务到集群
        if (OuyuncMessageContentTypeEnum.ACK_CONTENT.getType() == contentType) {
            // 清空本地 missAckTimes 次数
            MessageServerContext.clusterClientMissAckTimesCache.delete(remoteServerAddress);
            // 如果相等则添加到注册表，清空packet 中 message 的 路由信息，其实这里不需要清空了（不向外转发）
            // 这一步完成了两个操作:
            // 1，将remoteServerAddress 获取channel pool,
            // 2，如果之前存在该remoteServerAddress 则不进行存储操作，否则存储该channelpool
            MessageServerContext.clusterActiveServerRegistryTableCache.putIfAbsent(remoteServerAddress, MessageClientPool.clientSimpleChannelPoolMap.get(remoteServerAddress));
        }
    }


}
