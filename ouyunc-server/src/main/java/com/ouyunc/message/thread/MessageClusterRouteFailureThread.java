package com.ouyunc.message.thread;


import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息路由失败处理线程
 **/
public class MessageClusterRouteFailureThread implements Runnable {
    private static final Logger log = LoggerFactory.getLogger(MessageClusterRouteFailureThread.class);

    /**
     * 消息
     */
    private final Packet packet;

    public MessageClusterRouteFailureThread(Packet packet) {
        this.packet = packet;
    }

    /**
     * @Author fzx
     * @Description 路由异常后的重试
     */
    @Override
    public void run() {
        log.warn("获取不到可用的服务连接！packetId: {},开始进行重试...", packet.getPacketId());
        Metadata metadata = packet.getMessage().getMetadata();
        int currentRetry = metadata.getCurrentRetry();
        currentRetry++;
        // 清空消息中的列表，添加重试次数+1
        metadata.setCurrentRetry(currentRetry);
        metadata.setFromServerAddress(null);
        metadata.setRoutingTables(null);
        // targetSocketAddress 不改变
        if (log.isDebugEnabled()) {
            log.debug("正在进行第 {} 次重试消息 packetId:{} ", currentRetry, packet.getPacketId());
        }
        if (currentRetry < MessageServerContext.serverProperties().getClusterMessageRetry()) {
            // 重试次数+1，清空消息中的曾经路由过的服务，封装消息，找到目标主机
            // retry 去处理
            MessageHelper.asyncSendMessage(packet, metadata.getTarget());
            return;
        }
        // 如果重试之后还是出现服务不通，则进行服务的下线处理(这一步在内置客户端心跳保活时处理，这里不做服务下线的处理)，也就是将目标主机从本服务的注册表中删除（如果存在），其他服务上的注册表不做同步更新
        // 其实这里注册表中的数据移除不移除没什么太大意义
        log.error("已经重试 {} 次,也没解决问题,该消息packetId : {}将被丢弃！", MessageServerContext.serverProperties().getClusterMessageRetry(), packet.getPacketId());
    }
}
