package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageTypeEnum;
import com.ouyunc.base.constant.enums.NetworkEnum;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.model.Target;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisPipelineSupport;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.cluster.lease.LocalNodeConnCounter;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.protocol.NativePacketProtocol;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * @author fzx
 * @description 客户端助手
 */
public class ClientHelper {

    private static final Logger log = LoggerFactory.getLogger(ClientHelper.class);

    private  static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    private  static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /***
     * @author fzx
     * @description 客户端绑定登录信息（兼容入口，内部调用 {@link #bindAsync}）。
     */
    public static void bind(ChannelHandlerContext ctx, LoginClientInfo loginClientInfo) {
        bindAsync(ctx, loginClientInfo);
    }

    /***
     * @author fzx
     * @description 客户端绑定登录信息：先写 Redis，成功后再注册本地表与 Channel 属性。
     *              返回 CompletableFuture，在集群可见且本机可路由后完成（调用方再发登录成功 ACK）。
     */
    public static CompletableFuture<Void> bindAsync(ChannelHandlerContext ctx, LoginClientInfo loginClientInfo) {
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        Channel channel = ctx.channel();
        return CompletableFuture.runAsync(() -> {
                    doBindRemote(loginClientInfo, comboIdentity);
                    rollbackRemoteIfChannelClosed(loginClientInfo, comboIdentity, channel);
                }, ThreadPoolManager.messageProcessorExecutor())
                .thenCompose(unused -> runOnEventLoop(channel, () -> {
                    if (!channel.isActive()) {
                        rollbackRemoteIfChannelClosed(loginClientInfo, comboIdentity, channel);
                        throw new MessageException("channel 已关闭，放弃完成本地注册");
                    }
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN, loginClientInfo);
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT,
                            loginClientInfo.getHeartBeatTimeout());
                    registerLocal(comboIdentity, ctx, loginClientInfo.getAppKey());
                }))
                .whenComplete((unused, ex) -> {
                    if (ex != null) {
                        unregisterLocal(comboIdentity, ctx, loginClientInfo.getAppKey());
                        rollbackRemoteIfChannelClosed(loginClientInfo, comboIdentity, channel);
                    }
                });
    }

    /**
     * TCP 已断则摘掉刚写入的目录，避免幽灵在线。lastLoginTime 不匹配说明已被新会话覆盖。
     */
    private static void rollbackRemoteIfChannelClosed(LoginClientInfo loginClientInfo, String comboIdentity, Channel channel) {
        if (channel != null && channel.isActive()) {
            return;
        }
        String loginKey = CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity);
        if (!tryRunWithBindLock(loginClientInfo.getAppKey(), comboIdentity, () -> {
            LoginClientInfo remote = MessageServerContext.remoteLoginClientInfoCache.get(loginKey);
            if (remote != null
                    && loginClientInfo.getLoginServerAddress().equals(remote.getLoginServerAddress())
                    && remote.getLastLoginTime() == loginClientInfo.getLastLoginTime()) {
                LoginSessionDirectory.unbind(loginClientInfo, comboIdentity);
            }
        })) {
            log.error("客户端: {} 关闭回滚获取锁失败", loginClientInfo);
        }
    }

    /**
     * 写入本地注册表；仅首次占用 combo 时加本机连接计数。覆盖旧 ctx 不加，由旧连接 close 按 ctx 摘除。
     */
    public static void registerLocal(String comboIdentity, ChannelHandlerContext ctx, String appKey) {
        ChannelHandlerContext previous = MessageServerContext.localLoginClientRegisterTable.asMap().put(comboIdentity, ctx);
        if (previous == null) {
            LocalNodeConnCounter.increment(appKey);
            NodeLeaseKeeper.scheduleConnPublish();
        }
    }

    /**
     * 按 ctx 摘本地表并减计数，避免踢人/绑定失败把新会话减掉或减两次。
     */
    public static void unregisterLocal(String comboIdentity, ChannelHandlerContext ctx, String appKey) {
        boolean removed;
        if (ctx != null) {
            removed = MessageServerContext.localLoginClientRegisterTable.asMap().remove(comboIdentity, ctx);
        } else {
            removed = MessageServerContext.localLoginClientRegisterTable.asMap().remove(comboIdentity) != null;
        }
        if (removed) {
            LocalNodeConnCounter.decrement(appKey);
            NodeLeaseKeeper.scheduleConnPublish();
        }
    }

    public static void unbindLocalRegisterTable(LoginClientInfo loginClientInfo) {
        unbindLocalRegisterTable(loginClientInfo, null);
    }

    public static void unbindLocalRegisterTable(LoginClientInfo loginClientInfo, ChannelHandlerContext ctx) {
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        unregisterLocal(comboIdentity, ctx, loginClientInfo.getAppKey());
    }

    private static CompletableFuture<Void> runOnEventLoop(Channel channel, Runnable action) {
        CompletableFuture<Void> future = new CompletableFuture<>();
        EventLoop eventLoop = channel.eventLoop();
        Runnable task = () -> {
            try {
                action.run();
                future.complete(null);
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        };
        if (eventLoop.inEventLoop()) {
            task.run();
        } else if (!eventLoop.isTerminated() && !eventLoop.isShutdown() && !eventLoop.isShuttingDown()) {
            eventLoop.execute(task);
        } else {
            future.completeExceptionally(new MessageException("channel.eventLoop 已终止或关闭，无法完成登录绑定"));
        }
        return future;
    }

    /**
     * 目录写仍加同端锁，避免同 identity+device 并发登录互相覆盖。
     */
    private static void doBindRemote(LoginClientInfo loginClientInfo, String comboIdentity) {
        RLock lock = MessageServerContext.redissonClient.getLock(
                CacheConstant.buildIdentityBindOrUnbindLockCacheKey(loginClientInfo.getAppKey(), comboIdentity));
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    LoginSessionDirectory.bind(loginClientInfo, comboIdentity);
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.error("客户端: {} 绑定登录信息失败,原因：获取分布式锁超时", loginClientInfo);
                throw new MessageException("客户端绑定登录信息失败：获取分布式锁超时");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("客户端绑定登录信息被中断: {}", loginClientInfo, e);
            throw new MessageException(e);
        } catch (Exception e) {
            log.error("客户端绑定登录信息失败,原因：{}", e.getMessage(), e);
            throw new MessageException(e);
        }
    }

    /**
     * 解绑/回滚抢锁失败会重试，避免幽灵 ONLINE。须在业务线程池调用，禁止 EventLoop。
     */
    public static boolean tryRunWithBindLock(String appKey, String comboIdentity, BindLockAction action) {
        RLock lock = MessageServerContext.redissonClient.getLock(
                CacheConstant.buildIdentityBindOrUnbindLockCacheKey(appKey, comboIdentity));
        for (int attempt = 1; attempt <= MessageConstant.BIND_LOCK_RETRY_TIMES; attempt++) {
            try {
                if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                    try {
                        action.run();
                        return true;
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("绑定锁等待被中断 appKey={} combo={}", appKey, comboIdentity);
                return false;
            } catch (Exception e) {
                log.error("绑定锁内业务失败 appKey={} combo={}", appKey, comboIdentity, e);
                return false;
            }
            log.warn("绑定锁超时，重试 {}/{} appKey={} combo={}",
                    attempt, MessageConstant.BIND_LOCK_RETRY_TIMES, appKey, comboIdentity);
        }
        return false;
    }

    @FunctionalInterface
    public interface BindLockAction {
        void run();
    }

    /***
     * @author fzx
     * @description 获取最终客户端心跳时间
     */
    public static int calculateClientHeartBeatTimeout(int heartBeatExpireTime) {
        int heartBeatTimeSeconds = MessageServerContext.serverProperties().getClientHeartBeatTimeout();
        if (heartBeatExpireTime > NumberConstant.NUMBER_0) {
            int x = Math.round(heartBeatExpireTime * MessageConstant.ZERO_POINT_FIVE);
            heartBeatTimeSeconds = x >= NumberConstant.NUMBER_5 ? heartBeatExpireTime + NumberConstant.NUMBER_5 : heartBeatExpireTime + x;
        }
        return heartBeatTimeSeconds;
    }

    public static Map<String, List<LoginClientInfo>> onlineAllBatch(String appKey, Set<String> identities) {
        Map<String, List<LoginClientInfo>> result = new HashMap<>(identities.size());
        Map<String, Set<String>> remainingCombos = new LinkedHashMap<>();
        for (String identity : identities) {
            List<LoginClientInfo> localHits = new ArrayList<>();
            Collection<Byte> deviceTypes = MessageServerContext.deviceTypeList(appKey, identity);
            Set<String> remoteCombos = new HashSet<>();
            for (Byte dt : deviceTypes) {
                String comboId = IdentityUtil.generalComboIdentity(appKey, identity, dt);
                ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(comboId);
                if (ctx != null) {
                    LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                    if (info != null && OnlineEnum.ONLINE.equals(info.getOnlineStatus())) {
                        localHits.add(info);
                        continue;
                    }
                }
                remoteCombos.add(comboId);
            }
            if (!localHits.isEmpty()) {
                result.put(identity, localHits);
            }
            if (!remoteCombos.isEmpty()) {
                remainingCombos.put(identity, remoteCombos);
            }
        }
        appendRemoteOnlineByRoute(appKey, remainingCombos, result);
        return result;
    }



    /**
     * @param identity          用户登录唯一标识，手机号，邮箱，身份证号码等
     * @param excludeDeviceTypeArr 需要排除的设备类型数组
     * @return String
     * @Author fzx
     * @Description 判断客户端是否在线, 如果在线返回该客户端所有在线连接的登录信息，支持多端登录
     */
    public static List<LoginClientInfo> onlineAll(String appKey, String identity, Byte... excludeDeviceTypeArr) {
        // 判断identity在该appKey下是否支持loginDeviceType该设备类型

        List<LoginClientInfo> loginClientInfoList = new ArrayList<>(NumberConstant.NUMBER_3);
        // 获取所有的实现DeviceType接口的枚举实例,先找定制化的客户所支持的设备类型
        Stream<Byte> deviceTypeStream = MessageServerContext.deviceTypeList(appKey, identity).stream();
        if (excludeDeviceTypeArr != null && excludeDeviceTypeArr.length > NumberConstant.NUMBER_0) {
            Set<Byte> excludeNames = Arrays.stream(excludeDeviceTypeArr)
                    .map(Byte::byteValue)
                    .collect(Collectors.toSet());
            deviceTypeStream = deviceTypeStream.filter(deviceType -> !excludeNames.contains(deviceType));
        }
        Set<String> comboIdentitySet = deviceTypeStream
                .map(deviceType -> IdentityUtil.generalComboIdentity(appKey, identity, deviceType))
                .collect(Collectors.toSet());

        // 先从本地注册表获取，如果在同一个服务器上或者不是集群
        Collection<ChannelHandlerContext> allLoginClientChannelHandlerContexts = MessageServerContext.localLoginClientRegisterTable.getAll(comboIdentitySet);
        allLoginClientChannelHandlerContexts.forEach(ctx -> {
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
                loginClientInfoList.add(loginClientInfo);
                comboIdentitySet.remove(IdentityUtil.generalComboIdentity(appKey, identity, loginClientInfo.getDeviceType()));
            }
        });
        if (comboIdentitySet.isEmpty()) {
            return loginClientInfoList;
        }
        Map<String, Long> liveEpochs = NodeLeaseKeeper.snapshot();
        Map<Object, Object> route = stringRedisTemplate.opsForHash()
                .entries(CacheConstant.buildLoginRouteCacheKey(appKey, identity));
        LoginSessionDirectory.evictDeadRoute(appKey, identity, route, liveEpochs);
        Set<String> liveLoginKeys = new HashSet<>();
        for (String comboIdentity : comboIdentitySet) {
            byte deviceType = IdentityUtil.revertDeviceType(comboIdentity);
            if (ImSessionPresence.isDeviceRouteLive(route, deviceType, liveEpochs)) {
                liveLoginKeys.add(CacheConstant.buildLoginCacheKey(appKey, comboIdentity));
            }
        }
        if (liveLoginKeys.isEmpty()) {
            return loginClientInfoList;
        }
        Collection<LoginClientInfo> remoteCacheLoginClientInfos =
                MessageServerContext.remoteLoginClientInfoCache.getAll(liveLoginKeys);
        if (CollectionUtils.isNotEmpty(remoteCacheLoginClientInfos)) {
            loginClientInfoList.addAll(remoteCacheLoginClientInfos);
        }
        return loginClientInfoList;
    }

    /**
     * 远程在线以路由 HASH + 租约为准，再管道 GET 登录 String 补发送元数据。
     */
    private static void appendRemoteOnlineByRoute(String appKey, Map<String, Set<String>> remainingCombos,
                                                  Map<String, List<LoginClientInfo>> result) {
        if (remainingCombos.isEmpty()) {
            return;
        }
        List<String> identities = new ArrayList<>(remainingCombos.keySet());
        RedisSerializer<String> keySer = stringRedisTemplate.getStringSerializer();
        List<Object> routeRaw = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String identity : identities) {
                connection.hashCommands().hGetAll(keySer.serialize(CacheConstant.buildLoginRouteCacheKey(appKey, identity)));
            }
            return null;
        });
        Map<String, Long> liveEpochs = NodeLeaseKeeper.snapshot();
        Set<String> liveLoginKeys = new HashSet<>();
        for (int i = 0; i < identities.size(); i++) {
            Object row = routeRaw == null || i >= routeRaw.size() ? null : routeRaw.get(i);
            Map<?, ?> route = row instanceof Map<?, ?> map ? map : Map.of();
            String identity = identities.get(i);
            LoginSessionDirectory.evictDeadRoute(appKey, identity, route, liveEpochs);
            for (String combo : remainingCombos.get(identity)) {
                byte deviceType = IdentityUtil.revertDeviceType(combo);
                if (ImSessionPresence.isDeviceRouteLive(route, deviceType, liveEpochs)) {
                    liveLoginKeys.add(CacheConstant.buildLoginCacheKey(appKey, combo));
                }
            }
        }
        if (liveLoginKeys.isEmpty()) {
            return;
        }
        List<Object> cached = RedisPipelineSupport.getValues(redisTemplate, liveLoginKeys);
        if (cached == null) {
            return;
        }
        for (Object item : cached) {
            if (item instanceof LoginClientInfo info) {
                result.computeIfAbsent(info.getIdentity(), k -> new ArrayList<>()).add(info);
            }
        }
    }


    /**
     * 获取某个端的登录信息,不暴露该接口
     * @param identity 客户端唯一标识
     * @param loginDeviceTypeValue 客户端登录的设备类型值
     * @return
     */
    private static LoginClientInfo online(String appKey, String identity, Byte loginDeviceTypeValue) {
        String comboIdentity = IdentityUtil.generalComboIdentity(appKey, identity, loginDeviceTypeValue);
        // 先从本地注册表获取，如果在同一个服务器上或者不是集群
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        if (ctx != null) {
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus()) && MessageContext.messageProperties.getLocalServerAddress().equals(loginClientInfo.getLoginServerAddress())) {
                return loginClientInfo;
            }
        }
        // 从redis 获取登录信息
        LoginClientInfo loginClientInfo = MessageServerContext.remoteLoginClientInfoCache.get(CacheConstant.buildLoginCacheKey(appKey, comboIdentity));
        if (isDirectoryOnline(loginClientInfo)) {
            return loginClientInfo;
        }
        return null;
    }

    /**
     * 登录 String 为 ONLINE 且节点租约 epoch 仍匹配。投递路径请优先走 {@link #onlineAll}（路由 HASH）。
     */
    public static boolean isDirectoryOnline(LoginClientInfo loginClientInfo) {
        if (loginClientInfo == null || !OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
            return false;
        }
        return NodeLeaseKeeper.isLive(loginClientInfo.getLoginServerAddress(), loginClientInfo.getNodeEpoch());
    }


    /**
     * 某 appKey 连接数：本机用内存计数（即时），其它存活节点用租约心跳写入的 HASH（最多一拍延迟）。
     */
    public static long connections(String appKey) {
        String localNodeId = NodeLeaseKeeper.localNodeId();
        List<String> remotes = remoteLiveNodeIds(localNodeId);
        long total = LocalNodeConnCounter.get(appKey);
        if (remotes.isEmpty()) {
            return total;
        }
        RedisSerializer<String> keySer = stringRedisTemplate.getStringSerializer();
        byte[] field = keySer.serialize(appKey);
        List<Object> rows = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String nodeId : remotes) {
                connection.hashCommands().hGet(keySer.serialize(CacheConstant.buildImNodeConnHashCacheKey(nodeId)), field);
            }
            return null;
        });
        if (rows != null) {
            for (Object raw : rows) {
                total += parseConnCount(raw);
            }
        }
        return total;
    }

    /**
     * 获取所有appKey
     * @return
     *
     */
    public static Set<String> appKeys() {
        return redisTemplate.<String, AppEntity>opsForHash().keys(CacheConstant.buildAppKeysCacheKey());
    }




    /**
     * 全量连接数：本机内存计数 + 其它存活节点 Redis HASH。
     */
    public static long connections() {
        String localNodeId = NodeLeaseKeeper.localNodeId();
        List<String> remotes = remoteLiveNodeIds(localNodeId);
        long totalConnections = LocalNodeConnCounter.total();
        if (remotes.isEmpty()) {
            return totalConnections;
        }
        RedisSerializer<String> keySer = stringRedisTemplate.getStringSerializer();
        List<Object> rows = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (String nodeId : remotes) {
                connection.hashCommands().hGetAll(keySer.serialize(CacheConstant.buildImNodeConnHashCacheKey(nodeId)));
            }
            return null;
        });
        if (rows == null) {
            return totalConnections;
        }
        for (Object row : rows) {
            if (!(row instanceof Map<?, ?> counts) || counts.isEmpty()) {
                continue;
            }
            for (Object raw : counts.values()) {
                totalConnections += parseConnCount(raw);
            }
        }
        return totalConnections;
    }

    private static List<String> remoteLiveNodeIds(String localNodeId) {
        List<String> remotes = new ArrayList<>();
        for (String nodeId : liveNodeIdsForConn()) {
            if (!localNodeId.equals(nodeId)) {
                remotes.add(nodeId);
            }
        }
        return remotes;
    }

    private static Set<String> liveNodeIdsForConn() {
        Set<String> nodeIds = new HashSet<>(NodeLeaseKeeper.snapshot().keySet());
        nodeIds.add(NodeLeaseKeeper.localNodeId());
        return nodeIds;
    }

    private static long parseConnCount(Object raw) {
        if (raw == null) {
            return NumberConstant.NUMBER_0;
        }
        if (raw instanceof Number number) {
            return Math.max(0L, number.longValue());
        }
        try {
            return Math.max(0L, Long.parseLong(raw.toString()));
        } catch (NumberFormatException e) {
            return NumberConstant.NUMBER_0;
        }
    }

    /**
     * SERVER_NOTIFY 广播：本机本地表投递；源节点按租约节点各发一份，dest 固定为对端节点地址。
     * <p>A↛C 时走 {@link MessageHelper} 中转，中间节点不改 dest、不二次全员扇出。
     */
    public static void broadcastServerNotify(String appKey, Packet packet) {
        deliverLocalBroadcast(appKey, packet);
        if (packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || packet.getMessage().getMetadata().isLocalBroadcastOnly()) {
            return;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()) {
            return;
        }
        String localNodeId = NodeLeaseKeeper.localNodeId();
        for (String nodeId : NodeLeaseKeeper.snapshot().keySet()) {
            if (localNodeId.equals(nodeId)) {
                continue;
            }
            Packet fanout = packet.clone();
            fanout.getMessage().getMetadata().setLocalBroadcastOnly(true);
            MessageHelper.asyncSendMessageWithoutInterceptor(fanout, buildBroadcastNodeTarget(appKey, nodeId));
        }
    }

    /**
     * 节点级广播信封：targetServerAddress 为最终要扫本地连接的 IM 节点，不是下一跳。
     */
    private static Target buildBroadcastNodeTarget(String appKey, String destNodeId) {
        return Target.newBuilder()
                .appKey(appKey)
                .targetServerAddress(destNodeId)
                .protocol(NativePacketProtocol.OUYUNC.getProtocol())
                .protocolVersion(NativePacketProtocol.OUYUNC.getProtocolVersion())
                .build();
    }

    /**
     * 只投递本机已登录连接，不再向其他节点扇出。
     * <p>禁止在 Netty IO 线程扫全表；按 EventLoop 分组后在该 loop 上直接写出，避免全表拷贝和每连接 clone。
     */
    public static void deliverLocalBroadcast(String appKey, Packet packet) {
        if (packet == null) {
            return;
        }
        ThreadPoolManager.messageSendExecutor().execute(() -> deliverLocalBroadcastGrouped(appKey, packet));
    }

    /**
     * 弱一致遍历本机注册表，按 EventLoop 分桶后提交写出。每个 loop 一份 Packet clone，串行 setTarget。
     */
    private static void deliverLocalBroadcastGrouped(String appKey, Packet packet) {
        Map<EventLoop, List<ChannelHandlerContext>> byLoop = new IdentityHashMap<>();
        for (ChannelHandlerContext ctx : MessageServerContext.localLoginClientRegisterTable.asMap().values()) {
            if (!acceptLocalBroadcastCtx(appKey, ctx)) {
                continue;
            }
            byLoop.computeIfAbsent(ctx.channel().eventLoop(), loop -> new ArrayList<>()).add(ctx);
        }
        if (byLoop.isEmpty()) {
            return;
        }
        for (Map.Entry<EventLoop, List<ChannelHandlerContext>> entry : byLoop.entrySet()) {
            EventLoop loop = entry.getKey();
            if (loop.isTerminated() || loop.isShutdown() || loop.isShuttingDown()) {
                continue;
            }
            Packet loopPacket = packet.clone();
            if (loopPacket.getMessage() != null && loopPacket.getMessage().getMetadata() != null) {
                loopPacket.getMessage().getMetadata().setLocalBroadcastOnly(false);
            }
            List<ChannelHandlerContext> ctxs = entry.getValue();
            loop.execute(() -> writeLocalBroadcastOnEventLoop(loop, loopPacket, ctxs, 0));
        }
    }

    private static boolean acceptLocalBroadcastCtx(String appKey, ChannelHandlerContext ctx) {
        if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
            return false;
        }
        LoginClientInfo info = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        if (info == null || !OnlineEnum.ONLINE.equals(info.getOnlineStatus())) {
            return false;
        }
        return !StringUtils.isNotBlank(appKey) || appKey.equals(info.getAppKey());
    }

    /**
     * 同一 EventLoop 内分片写出：每批 {@link MessageConstant#IM_LOCAL_BROADCAST_EVENTLOOP_BATCH} 条后让出 loop。
     * 尽力而为，水位高则跳过，不发 SEND_FAIL。
     */
    private static void writeLocalBroadcastOnEventLoop(EventLoop loop, Packet loopPacket,
                                                       List<ChannelHandlerContext> ctxs, int from) {
        if (loopPacket.getMessage() == null || loopPacket.getMessage().getMetadata() == null) {
            return;
        }
        int end = Math.min(from + MessageConstant.IM_LOCAL_BROADCAST_EVENTLOOP_BATCH, ctxs.size());
        for (int i = from; i < end; i++) {
            ChannelHandlerContext ctx = ctxs.get(i);
            if (!PacketChannelWriter.isSendable(ctx)) {
                continue;
            }
            loopPacket.getMessage().getMetadata().setTarget(null);
            PacketChannelWriter.sendOnChannelBestEffort(ctx, loopPacket);
        }
        if (end < ctxs.size() && !loop.isShuttingDown() && !loop.isShutdown() && !loop.isTerminated()) {
            loop.execute(() -> writeLocalBroadcastOnEventLoop(loop, loopPacket, ctxs, end));
        }
    }

    /**
     * 通知本机全部已登录客户端：请主动断开并重连其他节点。
     * <p>服务端不 close 连接；由客户端收到 {@link MessageTypeEnum#SERVER_NOTIFY} 后自行断开重连。
     *
     * @return 成功下发通知的连接数
     */
    public static int notifyAllLocalClientsToReconnect() {
        long now = TimeUtil.currentTimeMillis();
        int notified = NumberConstant.NUMBER_0;
        int registrySize = NumberConstant.NUMBER_0;
        for (ChannelHandlerContext ctx : MessageServerContext.localLoginClientRegisterTable.asMap().values()) {
            registrySize++;
            if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
                continue;
            }
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(
                    ctx.channel(), MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginClientInfo == null) {
                continue;
            }
            notifyLocalClientServerDrain(loginClientInfo, now);
            notified++;
        }
        log.warn("notifyAllLocalClientsToReconnect 完成, notified={}, registryKey={}",
                notified, registrySize);
        return notified;
    }

    /**
     * 强制关闭本机仍存活的长连接（仅用于进程退出兜底；日常运维踢线请用 {@link #notifyAllLocalClientsToReconnect()}）。
     *
     * @return 尝试关闭的连接数
     */
    public static int forceCloseAllLocalClients() {
        List<Channel> closing = new ArrayList<>();
        int registrySize = NumberConstant.NUMBER_0;
        for (ChannelHandlerContext ctx : MessageServerContext.localLoginClientRegisterTable.asMap().values()) {
            registrySize++;
            if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
                continue;
            }
            closing.add(ctx.channel());
            ctx.close();
        }
        awaitChannelsClosed(closing, 5_000L);
        log.warn("forceCloseAllLocalClients 完成, attempted={}, registryKey={}", closing.size(), registrySize);
        return closing.size();
    }

    /**
     * 向本机在线会话发送「请主动重连」维护通知（不关闭连接）。
     */
    private static void notifyLocalClientServerDrain(LoginClientInfo loginClientInfo, long timestamp) {
        try {
            Message notifyMessage = new Message(
                    MessageContext.idGenerator().generateIdStr(),
                    null,
                    loginClientInfo.getIdentity(),
                    MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType(),
                    Serializer.JSON.serializeToString(
                            new ServerNotifyContent(MessageConstant.SERVER_DRAIN_KICK_NOTIFICATION)),
                    timestamp,
                    null);
            Packet notifyPacket = new Packet(
                    loginClientInfo.getProtocol(),
                    loginClientInfo.getProtocolVersion(),
                    MessageContext.idGenerator().generateId(),
                    loginClientInfo.getDeviceType(),
                    NetworkEnum.OTHER.getValue(),
                    NumberConstant.NUMBER_0,
                    Serializer.JSON.getValue(),
                    MessageTypeEnum.SERVER_NOTIFY.getType(),
                    notifyMessage);
            Target kickTarget = Target.newBuilder()
                    .appKey(loginClientInfo.getAppKey())
                    .targetIdentity(loginClientInfo.getIdentity())
                    .targetServerAddress(loginClientInfo.getLoginServerAddress())
                    .deviceType(loginClientInfo.getDeviceType())
                    .protocol(loginClientInfo.getProtocol())
                    .protocolVersion(loginClientInfo.getProtocolVersion())
                    .build();
            MessageHelper.syncSendMessageWithoutInterceptor(notifyPacket, kickTarget);
        } catch (Exception e) {
            log.warn("发送维护重连通知失败 identity={}: {}", loginClientInfo.getIdentity(), e.getMessage());
        }
    }

    private static void awaitChannelsClosed(List<Channel> channels, long timeoutMillis) {
        if (channels.isEmpty()) {
            return;
        }
        long deadline = System.currentTimeMillis() + timeoutMillis;
        for (Channel channel : channels) {
            long remain = deadline - System.currentTimeMillis();
            if (remain <= 0) {
                break;
            }
            try {
                channel.closeFuture().await(remain, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("等待 channel 关闭被中断");
                break;
            }
        }
    }
}
