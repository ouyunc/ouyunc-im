package com.ouyunc.message.thread;

import com.ouyunc.base.model.ClusterRoute;
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
        ClusterRoute clusterRoute = metadata.getClusterRoute();
        int currentRetry = clusterRoute.getCurrentRetry();
        int maxRetry = MessageServerContext.serverProperties().getClusterMessageRetry();
        if (currentRetry >= maxRetry) {
            log.error("已经重试 {} 次,也没解决问题,该消息packetId : {}将被丢弃！", maxRetry, packet.getPacketId());
            return;
        }
        int nextRetry = currentRetry + 1;
        // 清空消息中的列表，添加重试次数+1
        clusterRoute.setCurrentRetry(nextRetry);
        clusterRoute.setFromServerAddress(null);
        clusterRoute.setRoutingTables(null);
        // targetSocketAddress 不改变
        if (log.isDebugEnabled()) {
            log.debug("正在进行第 {} 次重试消息 packetId:{} ", nextRetry, packet.getPacketId());
        }
        // 本次是第 nextRetry 次重试；只有该次发送再次失败，失败线程才会在入口处判断是否丢弃。
        MessageHelper.asyncSendMessage(packet, clusterRoute.getTarget());
    }
}
