package com.ouyu.im.processor;

import com.ouyu.im.constant.ImConstant;
import com.ouyu.im.constant.enums.MessageEnum;
import com.ouyu.im.context.IMServerContext;
import com.ouyu.im.helper.MessageHelper;
import com.ouyu.im.innerclient.pool.IMClientPool;
import com.ouyu.im.packet.Packet;
import com.ouyu.im.packet.message.Message;
import com.ouyu.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * @Author fangzhenxun
 * @Description: im内部使用的心跳消息类型
 * @Version V1.0
 **/
public class SynAckMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(SynAckMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.SYN_ACK;
    }


    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        // 集群内部处理消息，不做任何处理
        ctx.fireChannelRead(packet);
        return;
    }

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        // 判断收到的是syn还是ack
        // 如果是syn 则发送ack,如果是ack，则注册表增加服务
        Message synAckMessage = (Message) packet.getMessage();
        // 需要判断收到的消息是目的地是否是本服务器，如果不是在次将消息包传递出去，如果是则处理
        String synAck = synAckMessage.getContent();
        final String remoteServerAddressStr = synAckMessage.getFrom();
        final InetSocketAddress remoteServerAddress = SocketAddressUtil.convert2SocketAddress(remoteServerAddressStr);
        if (ImConstant.SYN.equals(synAck)) {
            // 收到syn 只能是第一次，syn不会在服务之间传递n次，所以这里封装message
            log.info("接收到远端IM服务：{}的SYN请求", remoteServerAddressStr);
            synAckMessage.setContent(ImConstant.ACK);
            // 获取本地服务地址
            // 这里是发送消息的起点
            synAckMessage.setFrom(IMServerContext.LOCAL_ADDRESS);
            synAckMessage.setTo(remoteServerAddressStr);
            synAckMessage.setToServerAddress(remoteServerAddressStr);
            // 这里需要使用客户端连接池来操作，因为可能ctx已经关闭了
            MessageHelper.deliveryMessage(remoteServerAddress, packet);
        }
        if (ImConstant.ACK.equals(synAck)) {
            // 如果收到ack,需要判断是本服务是否是目标服务
            final String targetServerAddress = synAckMessage.getToServerAddress();
            if (IMServerContext.LOCAL_ADDRESS.equals(targetServerAddress)) {
                // 清空missAckTimes 次数
                IMServerContext.MISS_ACK_TIMES_CACHE.invalidate(remoteServerAddress);
                // 如果相等则添加到注册表，清空路由信息，其实这里不需要清空了（不向外转发）
                //log.info("接收到远端IM服务：{}的ACK回应,现将该服务加入注册表中", remoteServerAddressStr);
                IMServerContext.CLUSTER_SERVER_REGISTRY_TABLE.get(remoteServerAddress, k -> IMClientPool.singleClientChannelPoolMap.get(remoteServerAddress));
                return;
            }
            // 不相等则继续传递
            MessageHelper.deliveryMessage(SocketAddressUtil.convert2SocketAddress(targetServerAddress), packet);

        }
    }

    @Override
    public void postProcess(ChannelHandlerContext ctx, Packet packet) {
        // do nothing
        log.info("syn-ack");
    }
}
