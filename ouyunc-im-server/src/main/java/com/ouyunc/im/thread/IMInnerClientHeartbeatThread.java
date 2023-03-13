package com.ouyunc.im.thread;


import cn.hutool.core.date.SystemClock;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.protocol.Protocol;
import com.ouyunc.im.serialize.Serializer;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.pool.ChannelPool;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * @Author fangzhenxun
 * @Description: 在集群中内置客户端的后置处理
 * @Version V3.0
 **/
public class IMInnerClientHeartbeatThread implements Runnable {
    private static Logger log = LoggerFactory.getLogger(IMInnerClientHeartbeatThread.class);
    /**
     * 线程池事件执行器
     */
    public static final EventExecutorGroup EVENT_EXECUTORS= new DefaultEventExecutorGroup(16);
    /**
     * 开始执行的时间
     */
    private static final Instant beginTime = Instant.now();

    /**
     * @param
     * @return void
     * @Author fangzhenxun
     * @Description 处理数据
     */
    @Override
    public void run() {
        log.debug("集群服务中当前存活的服务：{}",IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap().keySet());
        // 获取所有注册表中的key,每次更新注册表都会重新获取注册表信息
        Set<Map.Entry<InetSocketAddress, ChannelPool>> availableGlobalServer = MapUtil.mergerMaps(IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap(), IMServerContext.CLUSTER_GLOBAL_SERVER_REGISTRY_TABLE.asMap()).entrySet();
        Iterator<Map.Entry<InetSocketAddress, ChannelPool>> socketAddressChannelIterator = availableGlobalServer.iterator();
        while (socketAddressChannelIterator.hasNext()) {
            Map.Entry<InetSocketAddress, ChannelPool> socketAddressChannelPoolEntry = socketAddressChannelIterator.next();
            InetSocketAddress toInetSocketAddress = socketAddressChannelPoolEntry.getKey();
            // 给暂未连接的的服务（不在注册表中）进行重试连接发送syn去握手，需要回复ack
            String targetServerAddressStr = SocketAddressUtil.convert2HostPort(toInetSocketAddress);
            Message message = new Message(IMServerContext.SERVER_CONFIG.getLocalServerAddress(),targetServerAddressStr , MessageContentEnum.SYN_CONTENT.type(), SystemClock.now());
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
            Packet packet = new Packet(Protocol.OUYUC.getProtocol(), Protocol.OUYUC.getVersion(), SnowflakeUtil.nextId(), DeviceEnum.OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getLocalHost(), MessageEnum.SYN_ACK.getValue(), Encrypt.SymmetryEncrypt.NONE.getValue(), Serializer.PROTO_STUFF.getValue(),  message);
            // 内部客户端连接池异步传递消息syn ,尝试所有的路径去保持连通
            MessageHelper.sendMessage(packet, targetServerAddressStr);
            // 先获取给目标服务toInetSocketAddress 发送syn,没有回复ack的次数，默认从0开始
            AtomicInteger missAckTimes = IMServerContext.CLUSTER_INNER_CLIENT_MISS_ACK_TIMES_CACHE.get(toInetSocketAddress);
            // 判断次数是否到达规定的次数，默认3次（也就是说给目标服务器连续发送3次syn,没有一次得到响应ack）则进行服务下线处理，从活着的服务注册表移除该服务
            if (IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap().containsKey(toInetSocketAddress) && missAckTimes.incrementAndGet() > IMServerContext.SERVER_CONFIG.getClusterInnerClientHeartbeatWaitRetry()) {
                IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.delete(toInetSocketAddress);
                // 去除3.0.1 的服务下线相关逻辑，目前不做服务下线处理
            }
        }
        // 判断该服务所在的集群个数是否小于服务列表的半数（用于解决脑裂）, 启动服务30分钟后进行检测是否脑裂,如果满足则系统退出
        if (IMServerContext.SERVER_CONFIG.isClusterSplitBrainDetectionEnable() && (IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.size() + 1) <= (int)Math.ceil(availableGlobalServer.size()/2.0) && ChronoUnit.MINUTES.between(beginTime, Instant.now()) >= IMServerContext.SERVER_CONFIG.getClusterSplitBrainDetectionDelay()) {
            log.error("集群服务脑裂检测中，服务 {} 异常，开始注销...", IMServerContext.SERVER_CONFIG.getLocalServerAddress());
            IMServerContext.TTL_THREAD_LOCAL.get().stop();
        }
    }



}
