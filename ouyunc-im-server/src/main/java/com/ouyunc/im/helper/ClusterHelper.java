package com.ouyunc.im.helper;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.collection.ConcurrentHashSet;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.lock.DistributedLock;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.pool.ChannelPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.Set;

/**
 * @author fzx
 * 集群助手
 */
public class ClusterHelper {
    private static final Logger log = LoggerFactory.getLogger(ClusterHelper.class);


    /**
     * 撤诉
     * 撤回offlineServerAddress服务的举证
     *
     * @param offlineServerAddress
     */
    @DistributedLock(lockName = CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE)
    public static void withdrawal(String offlineServerAddress) {
        log.info("正在撤销服务: {} 的下线举证", offlineServerAddress);
        ConcurrentHashSet<String> hashSet = IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, offlineServerAddress);
        if (hashSet == null) {
            return;
        }
        if (hashSet.size() == 0) {
            IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, offlineServerAddress);
            return;
        }
        // 判断自己是否是举证人，如果是举证人则撤销
        if (hashSet.remove(IMServerContext.SERVER_CONFIG.getLocalServerAddress())) {
            IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.putHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, offlineServerAddress, hashSet);
        }
    }

    /**
     * @param targetServerAddressStr
     * @return void
     * @Author fangzhenxun
     * @Description 处理服务下线
     */
    @DistributedLock(lockName = CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE)
    public static void handlerServerOffline(String targetServerAddressStr, Set<Map.Entry<InetSocketAddress, ChannelPool>> availableGlobalServer) {
        log.warn("正在处理下线服务：{}", targetServerAddressStr);
        // 先上报异常
        ConcurrentHashSet<String> hashSet = IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.getHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, targetServerAddressStr);
        if (hashSet == null) {
            hashSet = new ConcurrentHashSet<>();
        }
        hashSet.add(IMServerContext.SERVER_CONFIG.getLocalServerAddress());
        IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.putHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE, targetServerAddressStr, hashSet);
        // 在分布式缓存中记录该服务下线，如果超过一半的服务都认为该服务不可达，则进行下线处理
        // 每个服务都会去处理下线但未交接任务的服务
        Map<String, ConcurrentHashSet> offlineServerMap = IMServerContext.CLUSTER_SERVER_OFFLINE_CACHE.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.CLUSTER_SERVER + CacheConstant.OFFLINE);
        if (MapUtil.isNotEmpty(offlineServerMap)) {
            // 每个服务都去处理所有的下线服务，这样避免，当某个服务处理是突然下线，造成当前处理异常
            offlineServerMap.forEach((offlineServerAddress, witnessServerAddressSet) -> {
                if (CollectionUtil.isNotEmpty(witnessServerAddressSet) && witnessServerAddressSet.size() >= (int) Math.ceil(availableGlobalServer.size() / 2.0)) {
                    // @TODO 该offlineServerAddress下线服务的举证服务已经过半,现进行任务的移交处理
                    // 根据策略选举某个服务来接管下线的服务
                    // do something
                    // 最后打上标记已经处理接管任务了,这里才是最终下线了，任务也移交给其他服务处理
                    log.error("服务: {} 下线了！举证服务: {}", offlineServerAddress, witnessServerAddressSet);
                }
            });

        }
    }
}
