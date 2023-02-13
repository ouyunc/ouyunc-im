package com.ouyunc.im.thread;


import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import cn.hutool.core.date.SystemClock;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.DeviceEnum;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.constant.enums.NetworkEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.encrypt.Encrypt;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.lock.DistributedLock;
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
            if (IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.asMap().containsKey(toInetSocketAddress) && missAckTimes.incrementAndGet() >= IMServerContext.SERVER_CONFIG.getClusterInnerClientHeartbeatWaitRetry()) {
                IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.delete(toInetSocketAddress);
                // 检测到socketAddress服务下线，进行异步任务处理,
                EVENT_EXECUTORS.execute(() -> handlerServerOffline(toInetSocketAddress, availableGlobalServer));
            }
        }
        // 判断该服务所在的集群个数是否小于服务列表的半数（用于解决脑裂）, 启动服务30分钟后进行检测是否脑裂,如果满足则系统退出
        if (IMServerContext.SERVER_CONFIG.isClusterSplitBrainDetectionEnable() && IMServerContext.CLUSTER_ACTIVE_SERVER_REGISTRY_TABLE.size() < (int)Math.ceil(availableGlobalServer.size()/2.0) && ChronoUnit.MINUTES.between(beginTime, Instant.now()) >= IMServerContext.SERVER_CONFIG.getClusterSplitBrainDetectionDelay()) {
            log.error("集群服务脑裂检测中，服务 {} 异常，开始注销...", IMServerContext.SERVER_CONFIG.getLocalServerAddress());
            IMServerContext.TTL_THREAD_LOCAL.get().stop();
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 异步处理服务下线
     * @param inetSocketAddress
     * @return void
     */
    @DistributedLock(lockName = CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE)
    public void handlerServerOffline(InetSocketAddress inetSocketAddress, Set<Map.Entry<InetSocketAddress, ChannelPool>> availableGlobalServer){
        String toServerAddress = SocketAddressUtil.convert2HostPort(inetSocketAddress);
        log.warn("正在处理下线服务：{}",toServerAddress);
        // 先上报异常
        ConcurrentHashSet<String> hashSet = IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.IM +  CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, toServerAddress);
        if (hashSet == null) {
            hashSet = new ConcurrentHashSet<>();
        }
        hashSet.add(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
        IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.putHash(CacheConstant.OUYUNC + CacheConstant.IM +  CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, toServerAddress, hashSet);
        // 在分布式缓存中记录该服务下线，如果超过一半的服务都认为该服务不可达，则进行下线处理
        // 每个服务都会去处理下线但未交接任务的服务
        Map<String, ConcurrentHashSet> offlineServerMap = IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM +  CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE);
        if (MapUtil.isNotEmpty(offlineServerMap)) {
            // 每个服务都去处理所有的下线服务，这样避免，当某个服务处理是突然下线，造成当前处理异常
            offlineServerMap.forEach((offlineServerAddress, witnessServerAddressSet)->{
                if (CollectionUtil.isNotEmpty(witnessServerAddressSet) && witnessServerAddressSet.size() >= (int)Math.ceil(availableGlobalServer.size()/2.0)) {
                    // @TODO 该offlineServerAddress下线服务的举证服务已经过半,现进行任务的移交处理
                    // 根据策略选举某个服务来接管下线的服务
                    // do something
                    log.error("服务: {} 下线了！", offlineServerAddress);
                    // 最后打上标记已经处理接管任务了,这里才是最终下线了，任务也移交给其他服务处理
                    IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM +  CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, offlineServerAddress);
                }
            });

        }
    }
}
