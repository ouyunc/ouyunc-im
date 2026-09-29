package com.ouyunc.message.helper;

import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.SendCallback;
import com.ouyunc.base.model.SendResult;
import com.ouyunc.base.constant.enums.SendStatusEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.message.cluster.client.pool.MessageClientPool;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.pool.ChannelPool;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 集群健康探测专用写出器。
 *
 * <p>它不是客户端消息发送入口：允许使用尚未进入 active 的连接池，失败不执行集群业务路由，
 * 仅把结果交给节点健康管理逻辑。</p>
 */
public final class ClusterProbeSender {

    private static final Logger log = LoggerFactory.getLogger(ClusterProbeSender.class);

    private ClusterProbeSender() {
    }

    public static void send(Packet packet, String serverAddress, SendCallback sendCallback) {
        if (packet == null || StringUtils.isBlank(serverAddress)) {
            fail(packet, sendCallback, new MessageException("集群探测缺少目标节点"));
            return;
        }
        ChannelPool channelPool = MessageServerContext.clusterActiveServerRegistryTableCache.get(serverAddress);
        if (channelPool == null) {
            channelPool = MessageServerContext.clusterGlobalServerRegistryTableCache.get(serverAddress);
        }
        if (channelPool == null) {
            log.warn("有新的服务 {} 加入集群，正在尝试与其确认 ACK", serverAddress);
            channelPool = MessageClientPool.clientSimpleChannelPoolMap.get(serverAddress);
        }
        if (channelPool == null) {
            fail(packet, sendCallback, new MessageException("获取不到集群探测连接池: " + serverAddress));
            return;
        }
        MessageSender.writeViaClusterPool(packet, channelPool, serverAddress, sendCallback, false);
    }

    private static void fail(Packet packet, SendCallback sendCallback, Throwable error) {
        if (sendCallback == null) {
            return;
        }
        sendCallback.onCallback(SendResult.builder()
                .sendStatus(SendStatusEnum.SEND_FAIL)
                .packet(packet)
                .exception(error)
                .build());
    }
}
