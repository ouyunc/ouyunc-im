package com.ouyunc.im.processor;

import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.innerclient.pool.IMInnerClientPool;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.SnowflakeUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: im内部客户端使用的心跳消息syn-ack处理器
 **/
public class SynAckMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(SynAckMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.SYN_ACK;
    }

    /**
     * 内部客户端 syn-ack的逻辑处理, 注意：记住from 和 to 代表的什么含义以及所存储的值是什么
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // 判断收到的是syn还是ack
        // 如果是syn 则发送ack,如果是ack，则注册表增加服务
        Message synAckMessage = (Message) packet.getMessage();
        // 需要判断收到的消息是目的地是否是本服务器，如果不是在次将消息包传递出去，如果是则处理
        int contentType = synAckMessage.getContentType();
        // 发送端服务器地址：ip:port
        final String remoteServerAddressStr = synAckMessage.getFrom();
        log.info("接收到远端IM服务：{}的 {} 请求", remoteServerAddressStr, MessageContentEnum.prototype(contentType).name());
        final InetSocketAddress remoteServerAddress = SocketAddressUtil.convert2SocketAddress(remoteServerAddressStr);

        // syn 可能是经过其他服务转发的，回去的ack可能是经过其他服务转发的
        if (MessageContentEnum.SYN_CONTENT.type() == contentType) {
            synAckMessage.setContentType(MessageContentEnum.ACK_CONTENT.type());
            synAckMessage.setFrom(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
            synAckMessage.setTo(remoteServerAddressStr);
            synAckMessage.setCreateTime(SystemClock.now());
            packet.setPacketId(SnowflakeUtil.nextId());
            packet.setIp(IMServerContext.SERVER_CONFIG.getLocalHost());
            // 这里需要使用客户端连接池来操作，因为可能ctx已经关闭了,使用异步传递
            MessageHelper.sendMessageSync(packet, remoteServerAddressStr);
            // 下面是解决集群中原有服务是如何发现新加入集群的服务的
            // 判断发到syn的服务是否在 全局服务注册表中，如果不在判断该服务的合法性，如果合法，尝试发送给对方syn进行探测，如果成功则将新加入集群中的服务添加到激活的路由表中
            if (IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.get(remoteServerAddress) == null && IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.get(remoteServerAddress) == null) {
                // 1,不在全局服务注册表中
                // 2,判断服务的合法性， 这里不做太多的验证，认为只要加入集群中的服务都是合法的，如果后期需要，在加入校验规则
                // 3,给对方发送syn心跳，去探测是否连通
                synAckMessage.setContentType(MessageContentEnum.SYN_CONTENT.type());
                packet.setPacketId(SnowflakeUtil.nextId());
                // 内部客户端连接池异步传递消息 syn ,尝试所有的路径去保持连通
                MessageHelper.sendMessage(packet, remoteServerAddressStr);
            }
        }
        if (MessageContentEnum.ACK_CONTENT.type() == contentType) {
            // 清空本地 missAckTimes 次数
            IMServerContext.CLUSTER_INNER_CLIENT_MISS_ACK_TIMES_CACHE.delete(remoteServerAddress);
            // 如果相等则添加到注册表，清空packet 中 message 的 路由信息，其实这里不需要清空了（不向外转发）
            // 这一步完成了两个操作:
            // 1，将remoteServerAddress 获取channel pool,
            // 2，如果之前存在该remoteServerAddress 则不进行存储操作，否则存储该channelpool
            IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.putIfAbsent(remoteServerAddress, IMInnerClientPool.singleClientChannelPoolMap.get(remoteServerAddress));
        }
    }

}
