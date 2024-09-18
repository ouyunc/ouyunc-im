package com.ouyunc.message.thread;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.DeviceTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageContentTypeEnum;
import com.ouyunc.base.constant.enums.OuyuncMessageTypeEnum;
import com.ouyunc.base.encrypt.Encrypt;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.MapUtil;
import com.ouyunc.core.listener.event.ServerOfflineEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.MessageHelper;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @author fzx
 * @description 消息客户端心跳线程
 */
public class MessageClusterSynAckThread implements Runnable{

    private static final Logger log = LoggerFactory.getLogger(MessageClusterSynAckThread.class);

    /**
     * 开始执行的时间
     */
    private static final Instant beginTime = Instant.now();

    /***
     * @author fzx
     * @description 集群间节点，心跳探活线程
     */
    @Override
    public void run() {
        log.debug("集群服务中当前存活的服务：{}", MessageServerContext.clusterActiveServerRegistryTableCache.asMap().keySet());
        // 获取所有注册表中的key,每次更新注册表都会重新获取注册表信息
        Set<Map.Entry<String, ChannelPool>> availableGlobalServer = MapUtil.mergerMaps(MessageServerContext.clusterActiveServerRegistryTableCache.asMap(), MessageServerContext.clusterGlobalServerRegistryTableCache.asMap()).entrySet();
        for (Map.Entry<String, ChannelPool> socketAddressChannelPoolEntry : availableGlobalServer) {
            String targetServerAddress = socketAddressChannelPoolEntry.getKey();
            // 给暂未连接的的服务（不在注册表中）进行重试连接发送syn去握手，需要回复ack
            Message message = new Message(MessageServerContext.serverProperties().getLocalServerAddress(), targetServerAddress, OuyuncMessageContentTypeEnum.SYN_CONTENT.getType(), Clock.systemUTC().millis());
            //  ==============针对以上packet 几种序列化对比: string = SYN=========
            //     packet            message
            // protoStuff 150b         80b  内部心跳只用protoStuff序列化/反序列化
            // protoBuf   156b         83b
            // kryo       140b         112b
            // json       355b         184b
            // hessian2   357b         221b
            // hessian    430b         235b
            // fst        650b         315b
            // jdk        500b         346b
            Packet packet = new Packet(NativePacketProtocol.OUYUNC.getProtocol(), NativePacketProtocol.OUYUNC.getProtocolVersion(), MessageServerContext.<Long>idGenerator().generateId(), DeviceTypeEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(), OuyuncMessageTypeEnum.SYN_ACK.getType(), message);
            // 内部客户端连接池异步传递消息syn ,尝试所有的路径去保持连通
            MessageHelper.asyncSendMessage(packet, Target.newBuilder().targetIdentity(targetServerAddress).build());
            // 先获取给目标服务toInetSocketAddress 发送syn,没有回复ack的次数，默认从0开始
            AtomicInteger missAckTimes = MessageServerContext.clusterClientMissAckTimesCache.get(targetServerAddress);
            // 判断次数是否到达规定的次数，默认3次（也就是说给目标服务器连续发送3次syn,没有一次得到响应ack）则进行服务下线处理，从活着的服务注册表移除该服务
            if (MessageServerContext.clusterActiveServerRegistryTableCache.asMap().containsKey(targetServerAddress) && missAckTimes.incrementAndGet() > MessageServerContext.serverProperties().getClusterClientHeartbeatWaitRetry()) {
                MessageServerContext.clusterActiveServerRegistryTableCache.delete(targetServerAddress);
                // 发送服务离线事件
                MessageServerContext.publishEvent(new ServerOfflineEvent(targetServerAddress), true);
            }
        }
        // 判断该服务所在的集群个数是否小于服务列表的半数（用于解决脑裂）, 启动服务30分钟后进行检测是否脑裂,如果满足则系统退出
        if (MessageServerContext.serverProperties().isClusterSplitBrainDetectionEnable() && (MessageServerContext.clusterActiveServerRegistryTableCache.sizeMap() + MessageConstant.ONE) <= availableGlobalServer.size() / 2 && ChronoUnit.MINUTES.between(beginTime, Instant.now()) >= MessageServerContext.serverProperties().getClusterSplitBrainDetectionDelayTime()) {
            log.error("集群服务脑裂检测中，服务 {} 异常，开始注销...", MessageServerContext.serverProperties().getLocalServerAddress());
            MessageServerContext.server.stop();
        }
    }
}
