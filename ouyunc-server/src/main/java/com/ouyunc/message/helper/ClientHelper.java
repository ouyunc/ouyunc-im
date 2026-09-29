package com.ouyunc.message.helper;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import com.ouyunc.base.packet.PacketCopyHelper;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ServerNotifyContent;
import com.ouyunc.base.serialize.Serializer;
import com.ouyunc.base.utils.*;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.cluster.lease.AppKeyConnQuotaSupport;
import com.ouyunc.message.cluster.lease.LocalNodeConnCounter;
import com.ouyunc.message.cluster.lease.NodeLeaseSnapshot;
import com.ouyunc.message.cluster.lease.SessionNodeState;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
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

/**
 * @author fzx
 * @description å®¢æ·ç«¯å©æ
 */
public class ClientHelper {

    private static final Logger log = LoggerFactory.getLogger(ClientHelper.class);

    private  static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    private  static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * æç¨æ·ç¼å­å®æ´è®¾å¤è·¯ç±å¿«ç§ï¼ä¾ onlineAll ä½¿ç¨ã
     * åªç¼å­éç©ºç»æï¼æ°ç»å½ç¨æ·ä¸ä¼è¢«è´ç¼å­ç­æè¯¯å¤ä¸ºç¦»çº¿ã
     */
    private static final Cache<RouteIdentityKey, Map<Byte, String>> ROUTE_SNAPSHOT_CACHE = Caffeine.newBuilder()
            .maximumSize(MessageConstant.LOGIN_ROUTE_LOCAL_CACHE_MAX_SIZE)
            .expireAfterWrite(MessageConstant.LOGIN_ROUTE_LOCAL_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
            .build();

    /**
     * æè®¾å¤ç¼å­åå­æ®µè·¯ç±ï¼ä¾ onlineDevice ä½¿ç¨ï¼é¿åä¸ºäºä¸ä¸ªè®¾å¤æ§è¡ HGETALLã
     * onlineAll ä» Redis åå¾å®æ´å¿«ç§åä¼åæ­¥é¢ç­æ¬ç¼å­ã
     */
    private static final Cache<RouteDeviceKey, String> ROUTE_DEVICE_CACHE = Caffeine.newBuilder()
            .maximumSize(MessageConstant.LOGIN_ROUTE_LOCAL_CACHE_MAX_SIZE)
            .expireAfterWrite(MessageConstant.LOGIN_ROUTE_LOCAL_CACHE_TTL_MILLIS, TimeUnit.MILLISECONDS)
            .build();



    /***
     * @author fzx
     * @description å®¢æ·ç«¯ç»å®ç»å½ä¿¡æ¯ï¼åå¸å¼éå CAS å Redisï¼æç»æ´æ§ lastLoginTimeï¼ï¼
     *              æåååæ³¨åæ¬å°è¡¨ä¸ Channel å±æ§ãFuture ç»æä¸ºè¢«é¡¶æ¿çä¸ä¸ä¼è¯ï¼å¯ç©ºï¼ï¼
     *              è°ç¨æ¹åºå¨ç¡®è®¤ä»æ¥æç®å½ååè¸¢æ§è¿æ¥å¹¶åç»å½ ACKã
     */
    public static CompletableFuture<LoginClientInfo> bindAsync(ChannelHandlerContext ctx, LoginClientInfo loginClientInfo) {
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        Channel channel = ctx.channel();
        return CompletableFuture.supplyAsync(() -> {
                    LoginClientInfo previous = doBindRemote(loginClientInfo, comboIdentity);
                    rollbackRemoteIfChannelClosed(loginClientInfo, comboIdentity, channel);
                    return previous;
                }, ThreadPoolManager.messageProcessorExecutor())
                .thenCompose(previous -> runOnEventLoop(channel, () -> {
                    if (!channel.isActive()) {
                        rollbackRemoteAsync(loginClientInfo, comboIdentity, channel);
                        throw new MessageException("channel å·²å³é­ï¼æ¾å¼å®ææ¬å°æ³¨å");
                    }
                    ChannelHandlerContext staleLocal =
                            MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN, loginClientInfo);
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT,
                            loginClientInfo.getHeartBeatTimeout());
                    registerLocal(comboIdentity, ctx, loginClientInfo.getAppKey());
                    // æ¬æºæ§è¿æ¥ï¼å¨è¦çæ³¨åè¡¨åå·²ååºå¼ç¨ï¼ç»å®èåºåç«å³å³é­ï¼é¿ååå¨çº¿
                    closeStaleLocalIfPresent(staleLocal, ctx);
                }).thenApply(unused -> previous))
                .whenComplete((unused, ex) -> {
                    if (ex != null) {
                        unregisterLocal(comboIdentity, ctx, loginClientInfo.getAppKey());
                        rollbackRemoteAsync(loginClientInfo, comboIdentity, channel);
                    }
                });
    }

    /**
     * ç»å®å®æåæ ¡éªæ¬ç«¯æ¯å¦ä»æ¯ç®å½ä¸»äººï¼é²æ­¢è§£éåè¢«æ´æ°ä¼è¯è¦çå´ç»§ç»­å ACKï¼ã
     * ä¼åè¯»è·¯ç± HASH å°å­æ®µï¼é¿ååååºååæ´ä»½ LoginClientInfo JSONã
     */
    public static boolean stillOwnsDirectory(LoginClientInfo loginClientInfo) {
        if (loginClientInfo == null) {
            return false;
        }
        String routeKey = CacheConstant.buildLoginRouteCacheKey(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        Object raw = stringRedisTemplate.opsForHash()
                .get(routeKey, String.valueOf(loginClientInfo.getDeviceType()));
        String encoded = raw == null ? null : raw.toString();
        if (StringUtils.isNotBlank(encoded)) {
            long routeTs = ImRouteCodec.lastLoginTime(encoded);
            if (routeTs > 0L) {
                return routeTs == loginClientInfo.getLastLoginTime()
                        && Objects.equals(ImRouteCodec.nodeId(encoded), loginClientInfo.getLoginServerAddress());
            }
        }
        return false;
    }

    private static void closeStaleLocalIfPresent(ChannelHandlerContext staleLocal, ChannelHandlerContext currentCtx) {
        if (staleLocal == null || staleLocal == currentCtx || staleLocal.channel() == null
                || !staleLocal.channel().isActive()) {
            return;
        }
        if (staleLocal.channel().eventLoop().inEventLoop()) {
            staleLocal.close();
            return;
        }
        staleLocal.channel().eventLoop().execute(staleLocal::close);
    }

    /**
     * TCP å·²æ­åææååå¥çç®å½ï¼é¿åå¹½çµå¨çº¿ãlastLoginTime ä¸å¹éè¯´æå·²è¢«æ°ä¼è¯è¦çã
     */
    private static void rollbackRemoteIfChannelClosed(LoginClientInfo loginClientInfo, String comboIdentity, Channel channel) {
        if (channel != null && channel.isActive()) {
            return;
        }
        if (!tryRunWithBindLock(loginClientInfo.getAppKey(), comboIdentity, () -> {
            if (stillOwnsDirectory(loginClientInfo)) {
                LoginSessionDirectoryHelper.unbind(loginClientInfo);
                invalidateRouteCacheEverywhere(loginClientInfo);
            }
        })) {
            log.error("å®¢æ·ç«¯: {} å³é­åæ»è·åéå¤±è´¥", loginClientInfo);
        }
    }

    /**
     * è¿ç¨éä¸ Redis åæ»å§ç»ç¦»å¼ EventLoopï¼æç»æ¶ç±è·¯ç± CAS åæ»/æ­»è·¯ç±æ¸çååºã
     */
    private static void rollbackRemoteAsync(LoginClientInfo loginClientInfo, String comboIdentity, Channel channel) {
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(
                    () -> rollbackRemoteIfChannelClosed(loginClientInfo, comboIdentity, channel));
        } catch (RuntimeException e) {
            log.error("æäº¤ç»å½è¿ç¨åæ»ä»»å¡å¤±è´¥ combo={}ï¼ç­å¾æ­»è·¯ç±æ¸ç", comboIdentity, e);
        }
    }

    /**
     * åå¥æ¬å°æ³¨åè¡¨ãæ¬æºè®¡æ°å·²å¨ tryReserve å è¿ï¼è¿éåªæ¶è´¹é¢å æ è®°ï¼ä¸åäºæ¬¡ INCRã
     * è¦çæ§ ctx ä¸å ä¹ä¸åï¼æ§è¿æ¥å³è¿æ¶æéé¢å±æ§æé£ä¸æ ¼è¿æã
     */
    public static void registerLocal(String comboIdentity, ChannelHandlerContext ctx, String appKey) {
        boolean reserved = ctx != null && Boolean.TRUE.equals(
                ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED));
        if (reserved) {
            ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED, null);
        }
        ChannelHandlerContext previous = MessageServerContext.localLoginClientRegisterTable.asMap().put(comboIdentity, ctx);
        if (previous == null && !reserved) {
            LocalNodeConnCounter.increment(appKey);
            SessionNodeState.scheduleConnPublish();
        }
    }

    /**
     * æå³é­ä¸­ç Channel ææ¬å°è¡¨å¹¶åè®¡æ°ï¼é¿åè¸¢äºº/ç»å®å¤±è´¥ææ°ä¼è¯åææåä¸¤æ¬¡ã
     */
    public static void unregisterLocal(String comboIdentity, ChannelHandlerContext ctx, String appKey) {
        unregisterLocal(comboIdentity, ctx == null ? null : ctx.channel(), appKey);
    }

    /**
     * å³è¿é©å­å¿é¡»ä¼ æ­£å¨å³é­ç Channelï¼ä¸è½ç¨ç»å½æ¶æè·ç ctxã
     */
    public static void unregisterLocal(String comboIdentity, Channel channel, String appKey) {
        ChannelHandlerContext stored = MessageServerContext.localLoginClientRegisterTable.get(comboIdentity);
        boolean removed;
        if (channel == null) {
            removed = MessageServerContext.localLoginClientRegisterTable.asMap().remove(comboIdentity) != null;
        } else if (stored != null && stored.channel() == channel) {
            removed = MessageServerContext.localLoginClientRegisterTable.asMap().remove(comboIdentity, stored);
        } else {
            removed = false;
        }
        Channel quotaChannel = channel != null ? channel : (stored == null ? null : stored.channel());
        // ä»æç RESERVED è¯´æè¿æ²¡ registerLocalï¼æ¬æºä¸ Redis äº¤ç» releaseReservedIfNeededï¼è¿éä¸è½å¨ã
        boolean stillReserved = quotaChannel != null && Boolean.TRUE.equals(ChannelAttrUtil.getChannelAttribute(
                quotaChannel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_RESERVED));
        if (stillReserved) {
            return;
        }
        String quotaAppKey = takeQuotaAppKey(quotaChannel);
        if (removed) {
            releaseQuotaOffEventLoop(quotaChannel, quotaAppKey);
            return;
        }
        // åæºé¡¶å·ï¼æ°è¿æ¥ tryReserve å·² +1 å¹¶è¦çæ³¨åè¡¨ï¼æ§ Channel å¯¹ä¸ä¸ã
        // æ§è¿æ¥çé¢å æ è®°å·²å¨ registerLocal æ¸æï¼ä½ APP_KEY è¿å¨ï¼å¿é¡»æè¿ä¸æ ¼æ¬æºè®¡æ°è¿æã
        if (quotaAppKey == null) {
            return;
        }
        releaseQuotaOffEventLoop(quotaChannel, quotaAppKey);
    }

    /**
     * åèµ°éé¢ appKey å¹¶ç«å»æ¸å±æ§ï¼é¿åå³è¿é©å­åé¢å åæ»åéæ¾ä¸æ¬¡ã
     */
    private static String takeQuotaAppKey(Channel channel) {
        if (channel == null) {
            return null;
        }
        String quotaAppKey = ChannelAttrUtil.getChannelAttribute(
                channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY);
        if (quotaAppKey == null || quotaAppKey.isBlank()) {
            return null;
        }
        ChannelAttrUtil.setChannelAttribute(
                channel, MessageConstant.CHANNEL_ATTR_KEY_CONN_QUOTA_APP_KEY, null);
        return quotaAppKey;
    }

    /**
     * EventLoop ä¸åªæäº¤éæ¾ï¼ä¸å¡çº¿ç¨ä¸åæ­¥ Luaï¼é¿ååæ©ä¸æ¬¡å¿è·³å¯¹è´¦çªå£ã
     */
    private static void releaseQuotaOffEventLoop(Channel channel, String quotaAppKey) {
        if (quotaAppKey == null || quotaAppKey.isBlank()) {
            return;
        }
        if (channel != null && channel.eventLoop().inEventLoop()) {
            AppKeyConnQuotaSupport.releaseAsync(quotaAppKey);
            return;
        }
        AppKeyConnQuotaSupport.release(quotaAppKey);
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
            future.completeExceptionally(new MessageException("channel.eventLoop å·²ç»æ­¢æå³é­ï¼æ æ³å®æç»å½ç»å®"));
        }
        return future;
    }

    /**
     * ç®å½åå åç«¯éï¼è¯»åºä¸ä¸ä¼è¯ï¼è¥å¶ lastLoginTime ä¸¥æ ¼æ´å¤§åæç»ï¼è·¨èç¹ fencingï¼ï¼
     * å¦åè¦çç»å®å¹¶è¿åä¸ä¸ä¼è¯ä¾è°ç¨æ¹è¸¢çº¿ã
     */
    private static LoginClientInfo doBindRemote(LoginClientInfo loginClientInfo, String comboIdentity) {
        RLock lock = MessageServerContext.redissonClient.getLock(
                CacheConstant.buildIdentityBindOrUnbindLockCacheKey(loginClientInfo.getAppKey(), comboIdentity));
        try {
            // ä¸ä¼  leaseTimeï¼å¯ç¨ Redisson watchdogï¼é¿å Redis åæ¢æ¶ 5s éè¿æå¯¼è´åç»
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
                try {
                    // ç»å½ fencing å¿é¡»è¯»å Redis æå¨å¼ï¼ç¦æ­¢å½ä¸­ç­ TTL è·¯ç±ç¼å­ã
                    Object raw = stringRedisTemplate.opsForHash().get(
                            CacheConstant.buildLoginRouteCacheKey(
                                    loginClientInfo.getAppKey(), loginClientInfo.getIdentity()),
                            String.valueOf(loginClientInfo.getDeviceType()));
                    String authoritativeRoute = rawString(raw);
                    LoginClientInfo previous = StringUtils.isBlank(authoritativeRoute) ? null
                            : routeLoginInfo(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(),
                            loginClientInfo.getDeviceType(), authoritativeRoute);
                    if (previous != null
                            && previous.getLastLoginTime() > loginClientInfo.getLastLoginTime()) {
                        log.warn("ç»å½ fencing æç»æ´æ§ä¼è¯ combo={} previousTs={} currentTs={}",
                                comboIdentity, previous.getLastLoginTime(), loginClientInfo.getLastLoginTime());
                        throw new MessageException("ç»å½ç»å®å¤±è´¥ï¼å·²ææ´æ°ä¼è¯");
                    }
                    LoginSessionDirectoryHelper.bind(loginClientInfo);
                    invalidateRouteCacheEverywhere(loginClientInfo);
                    return previous;
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
            } else {
                log.error("å®¢æ·ç«¯: {} ç»å®ç»å½ä¿¡æ¯å¤±è´¥,åå ï¼è·ååå¸å¼éè¶æ¶", loginClientInfo);
                throw new MessageException("å®¢æ·ç«¯ç»å®ç»å½ä¿¡æ¯å¤±è´¥ï¼è·ååå¸å¼éè¶æ¶");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("å®¢æ·ç«¯ç»å®ç»å½ä¿¡æ¯è¢«ä¸­æ­: {}", loginClientInfo, e);
            throw new MessageException(e);
        } catch (MessageException e) {
            throw e;
        } catch (Exception e) {
            log.error("å®¢æ·ç«¯ç»å®ç»å½ä¿¡æ¯å¤±è´¥,åå ï¼{}", e.getMessage(), e);
            throw new MessageException(e);
        }
    }

    /**
     * è§£ç»/åæ»æ¢éå¤±è´¥ä¼éè¯ï¼é¿åå¹½çµ ONLINEãé¡»å¨ä¸å¡çº¿ç¨æ± è°ç¨ï¼ç¦æ­¢ EventLoopã
     * ä½¿ç¨ watchdog ç»­æï¼ç¦æ­¢åºå® 5s leaseã
     */
    public static boolean tryRunWithBindLock(String appKey, String comboIdentity, Runnable action) {
        RLock lock = MessageServerContext.redissonClient.getLock(
                CacheConstant.buildIdentityBindOrUnbindLockCacheKey(appKey, comboIdentity));
        for (int attempt = 1; attempt <= MessageConstant.BIND_LOCK_RETRY_TIMES; attempt++) {
            try {
                if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, TimeUnit.SECONDS)) {
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
                log.warn("ç»å®éç­å¾è¢«ä¸­æ­ appKey={} combo={}", appKey, comboIdentity);
                return false;
            } catch (Exception e) {
                log.error("ç»å®éåä¸å¡å¤±è´¥ appKey={} combo={}", appKey, comboIdentity, e);
                return false;
            }
            log.warn("ç»å®éè¶æ¶ï¼éè¯ {}/{} appKey={} combo={}",
                    attempt, MessageConstant.BIND_LOCK_RETRY_TIMES, appKey, comboIdentity);
        }
        return false;
    }


    /***
     * @author fzx
     * @description è·åæç»å®¢æ·ç«¯å¿è·³æ¶é´
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
        if (identities == null || identities.isEmpty()) {
            return Map.of();
        }
        int batch = MessageConstant.GROUP_FANOUT_ONLINE_LOOKUP_BATCH;
        if (identities.size() <= batch) {
            return onlineAllBatchChunk(appKey, identities);
        }
        Map<String, List<LoginClientInfo>> result = new HashMap<>(identities.size());
        Set<String> chunk = new HashSet<>(batch);
        for (String identity : identities) {
            if (identity == null) {
                continue;
            }
            chunk.add(identity);
            if (chunk.size() >= batch) {
                result.putAll(onlineAllBatchChunk(appKey, Set.copyOf(chunk)));
                chunk.clear();
            }
        }
        if (!chunk.isEmpty()) {
            result.putAll(onlineAllBatchChunk(appKey, Set.copyOf(chunk)));
        }
        return result;
    }

    private static Map<String, List<LoginClientInfo>> onlineAllBatchChunk(String appKey, Set<String> identities) {
        Map<String, List<LoginClientInfo>> result = new HashMap<>(identities.size());
        List<String> orderedIdentities = identities.stream().filter(Objects::nonNull).toList();
        List<RouteIdentityKey> cacheKeys = orderedIdentities.stream()
                .map(identity -> new RouteIdentityKey(appKey, identity))
                .toList();
        Map<RouteIdentityKey, Map<Byte, String>> cachedRoutes = ROUTE_SNAPSHOT_CACHE.getAll(
                cacheKeys, ClientHelper::loadRouteSnapshots);

        NodeLeaseSnapshot leaseSnapshot = SessionNodeState.currentSnapshot();
        Map<String, Long> liveEpochs = leaseSnapshot.epochs();
        for (String identity : orderedIdentities) {
            Map<Byte, String> routes = cachedRoutes.getOrDefault(
                    new RouteIdentityKey(appKey, identity), Map.of());
            for (Map.Entry<Byte, String> routeEntry : routes.entrySet()) {
                Byte deviceType = routeEntry.getKey();
                String encoded = routeEntry.getValue();
                if (!routeMatchesLiveLease(encoded, liveEpochs)) {
                    continue;
                }
                LoginClientInfo resolved = routeLoginInfo(appKey, identity, deviceType, encoded);
                if (resolved != null) {
                    result.computeIfAbsent(identity, ignored -> new ArrayList<>()).add(resolved);
                }
            }
        }
        return result;
    }

    /**
     * Caffeine æ¹éå è½½å¨ï¼ä»å¯¹æªå½ä¸­ç identity åèµ·ä¸æ¬¡ Pipelineï¼é¿å TTL è¾¹ççéå¤ HGETALLã
     * ç©ºè·¯ç±ä¸æ¾å¥è¿å Mapï¼å æ­¤ä¸ä¼å½¢æä¼é®è½æ°ç»å½çè´ç¼å­ã
     */
    private static Map<RouteIdentityKey, Map<Byte, String>> loadRouteSnapshots(
            Set<? extends RouteIdentityKey> missingKeys) {
        if (missingKeys == null || missingKeys.isEmpty()) {
            return Map.of();
        }
        List<RouteIdentityKey> orderedKeys = List.copyOf(missingKeys);
        RedisSerializer<String> keySerializer = stringRedisTemplate.getStringSerializer();
        List<Object> routeRows = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RouteIdentityKey key : orderedKeys) {
                connection.hashCommands().hGetAll(keySerializer.serialize(
                        CacheConstant.buildLoginRouteCacheKey(key.appKey(), key.identity())));
            }
            return null;
        });
        NodeLeaseSnapshot loadSnapshot = SessionNodeState.currentSnapshot();
        Map<RouteIdentityKey, Map<Byte, String>> loaded = new HashMap<>(orderedKeys.size());
        for (int index = 0; index < orderedKeys.size(); index++) {
            RouteIdentityKey key = orderedKeys.get(index);
            Object row = routeRows == null || index >= routeRows.size() ? null : routeRows.get(index);
            Map<?, ?> rawRoutes = row instanceof Map<?, ?> map ? map : Map.of();
            LoginSessionDirectoryHelper.evictDeadRoute(
                    key.appKey(), key.identity(), rawRoutes, loadSnapshot);
            Map<Byte, String> parsedRoutes = parseRoutes(rawRoutes);
            if (!parsedRoutes.isEmpty()) {
                loaded.put(key, parsedRoutes);
                parsedRoutes.forEach((deviceType, encoded) -> ROUTE_DEVICE_CACHE.put(
                        new RouteDeviceKey(key.appKey(), key.identity(), deviceType), encoded));
            }
        }
        return loaded;
    }



    /**
     * @param identity          ç¨æ·ç»å½å¯ä¸æ è¯ï¼ææºå·ï¼é®ç®±ï¼èº«ä»½è¯å·ç ç­
     * @param excludeDeviceTypeArr éè¦æé¤çè®¾å¤ç±»åæ°ç»
     * @return String
     * @Author fzx
     * @Description å¤æ­å®¢æ·ç«¯æ¯å¦å¨çº¿, å¦æå¨çº¿è¿åè¯¥å®¢æ·ç«¯ææå¨çº¿è¿æ¥çç»å½ä¿¡æ¯ï¼æ¯æå¤ç«¯ç»å½
     */
    public static List<LoginClientInfo> onlineAll(String appKey, String identity, Byte... excludeDeviceTypeArr) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return List.of();
        }
        List<LoginClientInfo> loginClientInfoList = new ArrayList<>(
                onlineAllBatchChunk(appKey, Set.of(identity)).getOrDefault(identity, List.of()));
        if (excludeDeviceTypeArr != null && excludeDeviceTypeArr.length > NumberConstant.NUMBER_0) {
            Set<Byte> excludeNames = Arrays.stream(excludeDeviceTypeArr)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
            if (!excludeNames.isEmpty()) {
                loginClientInfoList.removeIf(info -> excludeNames.contains(info.getDeviceType()));
            }
        }
        return loginClientInfoList;
    }

    /**
     * æ¥è¯¢æå®è®¾å¤æ¯å¦å¨çº¿ï¼ä¸å¨çº¿è¿å nullã
     */
    public static LoginClientInfo onlineDevice(String appKey, String identity, byte deviceType) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return null;
        }
        return online(appKey, identity, deviceType);
    }

    /**
     * è·åæä¸ªç«¯çç»å½ä¿¡æ¯
     */
    private static LoginClientInfo online(String appKey, String identity, Byte loginDeviceTypeValue) {
        String combo = IdentityUtil.generalComboIdentity(appKey, identity, loginDeviceTypeValue);
        ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
        LoginClientInfo local = ctx == null ? null : ChannelAttrUtil.getChannelAttribute(
                ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
        boolean localSendable = local != null && OnlineEnum.ONLINE.equals(local.getOnlineStatus())
                && MessageSender.isChannelSendable(ctx);
        try {
            LoginClientInfo resolved = routeLoginInfo(appKey, identity, loginDeviceTypeValue, null);
            if (resolved != null) {
                return resolved;
            }
            // ç§çº¦å¿«ç§ä¸å®æ´æ¶ä¸è½ç¨ Redis çä¸å®æ´è§å¾å¦å®æ¬æºçå® Channelã
            return localSendable && !SessionNodeState.currentSnapshot().isFresh() ? local : null;
        } catch (RuntimeException redisFailure) {
            if (localSendable) {
                log.debug("Redis è·¯ç±æä¸å¯ç¨ï¼ä½¿ç¨æ¬æºçå® Channel appKey={} identity={} deviceType={}",
                        appKey, identity, loginDeviceTypeValue);
                return local;
            }
            throw redisFailure;
        }
    }

    /**
     * ç±è½»éè·¯ç±æé è¿ç«¯æéç®æ ãè¯¥å¯¹è±¡ä¸æ¯å®æ´ç»å½ä¸ä¸æï¼åªåè®¸ç¨äºèç¹åç»ãè®¾å¤å®ä½å fencingã
     * åè®®ãselfSync ç­è¿æ¥å±æ§å¿é¡»ç±æç»è½å°èç¹ä»æ¬æº Channel è·åã
     */
    private static LoginClientInfo routeLoginInfo(String appKey, String identity, byte deviceType, String encodedRoute) {
        String encoded = encodedRoute;
        if (StringUtils.isBlank(encoded)) {
            RouteDeviceKey cacheKey = new RouteDeviceKey(appKey, identity, deviceType);
            encoded = ROUTE_DEVICE_CACHE.get(cacheKey, key -> {
                Object raw = stringRedisTemplate.opsForHash().get(
                        CacheConstant.buildLoginRouteCacheKey(key.appKey(), key.identity()),
                        String.valueOf(key.deviceType()));
                String loaded = rawString(raw);
                return StringUtils.isBlank(loaded) ? null : loaded;
            });
        }
        NodeLeaseSnapshot snapshot = SessionNodeState.currentSnapshot();
        if (!SessionNodeState.isCurrentSnapshot(snapshot) || !routeMatchesLiveLease(encoded, snapshot.epochs())) {
            return null;
        }
        String routeNode = ImRouteCodec.nodeId(encoded);
        long routeEpoch = ImRouteCodec.epoch(encoded);
        long routeLoginTime = ImRouteCodec.lastLoginTime(encoded);
        if (Objects.equals(SessionNodeState.localNodeId(), routeNode)) {
            String combo = IdentityUtil.generalComboIdentity(appKey, identity, deviceType);
            ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
            LoginClientInfo local = ctx == null ? null : ChannelAttrUtil.getChannelAttribute(
                    ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (local == null || !MessageSender.isChannelSendable(ctx)
                    || !OnlineEnum.ONLINE.equals(local.getOnlineStatus())
                    || local.getNodeEpoch() != routeEpoch
                    || local.getLastLoginTime() != routeLoginTime) {
                return null;
            }
            return local;
        }
        LoginClientInfo routeTarget = new LoginClientInfo();
        routeTarget.setAppKey(appKey);
        routeTarget.setIdentity(identity);
        routeTarget.setDeviceType(deviceType);
        routeTarget.setLoginServerAddress(routeNode);
        routeTarget.setNodeEpoch(routeEpoch);
        routeTarget.setLastLoginTime(routeLoginTime);
        routeTarget.setOnlineStatus(OnlineEnum.ONLINE);
        return routeTarget;
    }

    /**
     * ç»å½ç®å½åçæ¬æºåå¥æå é¤åä¸»å¨å¤±æãå¶å®èç¹ä¾é æç­ TTL æ¶æï¼
     * æç»è½å°ä»ä¼æ ¡éªèç¹ç§çº¦åæ¬æº Channelï¼ä¸æç¼å­ä½ä¸ºç®å½æææè¯æã
     */
    public static void invalidateRouteCacheEverywhere(LoginClientInfo loginClientInfo) {
        if (loginClientInfo == null || StringUtils.isAnyBlank(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity())) {
            return;
        }
        invalidateRouteCacheLocal(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(),
                loginClientInfo.getDeviceType());
        LoginRouteCacheInvalidationBus.publish(loginClientInfo.getAppKey(), loginClientInfo.getIdentity(),
                loginClientInfo.getDeviceType());
    }

    /**
     * ä»æ¸çå½å JVM çè·¯ç±ç¼å­ï¼ä¾å¤±æ Topic è®¢éåè°ä½¿ç¨ï¼ç¦æ­¢åæ¬¡åå¸ä»¥åå½¢ææ¶æ¯ç¯ã
     */
    public static void invalidateRouteCacheLocal(String appKey, String identity, byte deviceType) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return;
        }
        RouteIdentityKey identityKey = new RouteIdentityKey(appKey, identity);
        ROUTE_SNAPSHOT_CACHE.invalidate(identityKey);
        ROUTE_DEVICE_CACHE.invalidate(new RouteDeviceKey(appKey, identity, deviceType));
    }

    private static Map<Byte, String> parseRoutes(Map<?, ?> rawRoutes) {
        if (rawRoutes == null || rawRoutes.isEmpty()) {
            return Map.of();
        }
        Map<Byte, String> parsed = new HashMap<>(rawRoutes.size());
        for (Map.Entry<?, ?> entry : rawRoutes.entrySet()) {
            Byte deviceType = parseDeviceType(entry.getKey());
            String encoded = rawString(entry.getValue());
            if (deviceType != null && StringUtils.isNotBlank(encoded)) {
                parsed.put(deviceType, encoded);
            }
        }
        return parsed.isEmpty() ? Map.of() : Map.copyOf(parsed);
    }

    private record RouteIdentityKey(String appKey, String identity) {
    }

    private record RouteDeviceKey(String appKey, String identity, byte deviceType) {
    }

    private static boolean routeMatchesLiveLease(String encoded, Map<String, Long> liveEpochs) {
        if (StringUtils.isBlank(encoded) || liveEpochs == null || liveEpochs.isEmpty()) {
            return false;
        }
        String nodeId = ImRouteCodec.nodeId(encoded);
        Long liveEpoch = nodeId == null ? null : liveEpochs.get(nodeId);
        return liveEpoch != null && liveEpoch == ImRouteCodec.epoch(encoded);
    }

    private static Byte parseDeviceType(Object raw) {
        String value = rawString(raw);
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Byte.valueOf(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static String rawString(Object raw) {
        if (raw instanceof byte[] bytes) {
            return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
        }
        return raw == null ? null : String.valueOf(raw);
    }

    /**
     * æ appKey è¿æ¥æ°ï¼æ¬æºç¨åå­è®¡æ°ï¼å³æ¶ï¼ï¼å¶å®å­æ´»èç¹ç¨ç§çº¦å¿è·³åå¥ç HASHï¼æå¤ä¸æå»¶è¿ï¼ã
     */
    public static long connections(String appKey) {
        String localNodeId = SessionNodeState.localNodeId();
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
     * è·åææappKey
     * @return
     *
     */
    public static Set<String> appKeys() {
        return redisTemplate.<String, AppEntity>opsForHash().keys(CacheConstant.buildAppKeysCacheKey());
    }




    /**
     * å¨éè¿æ¥æ°ï¼æ¬æºåå­è®¡æ° + å¶å®å­æ´»èç¹ Redis HASHã
     */
    public static long connections() {
        String localNodeId = SessionNodeState.localNodeId();
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
        Set<String> nodeIds = new HashSet<>(SessionNodeState.snapshot().keySet());
        nodeIds.add(SessionNodeState.localNodeId());
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
     * SERVER_NOTIFY å¹¿æ­ï¼æ¬æºæ¬å°è¡¨æéï¼æºèç¹æç§çº¦èç¹ååä¸ä»½ï¼dest åºå®ä¸ºå¯¹ç«¯èç¹å°åã
     * <p>AâC æ¶èµ° {@link MessageSender} ä¸­è½¬ï¼ä¸­é´èç¹ä¸æ¹ destãä¸äºæ¬¡å¨åæåºã
     */
    public static void broadcastServerNotify(String appKey, Packet packet) {
        deliverLocalBroadcast(appKey, packet);
        if (packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || packet.getMessage().getMetadata().getClusterRoute().isLocalBroadcastOnly()) {
            return;
        }
        if (!MessageServerContext.serverProperties().isClusterEnable()) {
            return;
        }
        String localNodeId = SessionNodeState.localNodeId();
        for (String nodeId : SessionNodeState.snapshot().keySet()) {
            if (localNodeId.equals(nodeId)) {
                continue;
            }
            Packet fanout = packet.clone();
            fanout.getMessage().ensureMetadata().ensureClusterRoute().setLocalBroadcastOnly(true);
            MessageSender.resumeDelivery(fanout, buildBroadcastNodeTarget(appKey, nodeId));
        }
    }

    /**
     * èç¹çº§å¹¿æ­ä¿¡å°ï¼targetServerAddress ä¸ºæç»è¦æ«æ¬å°è¿æ¥ç IM èç¹ï¼ä¸æ¯ä¸ä¸è·³ã
     */
    private static Target buildBroadcastNodeTarget(String appKey, String destNodeId) {
        return Target.newBuilder()
                .appKey(appKey)
                .targetServerAddress(destNodeId)
                .build();
    }

    /**
     * åªæéæ¬æºå·²ç»å½è¿æ¥ï¼ä¸ååå¶ä»èç¹æåºã
     * <p>ç¦æ­¢å¨ Netty IO çº¿ç¨æ«å¨è¡¨ï¼æ EventLoop åç»åå¨è¯¥ loop ä¸ç´æ¥ååºï¼é¿åå¨è¡¨æ·è´åæ¯è¿æ¥ cloneã
     */
    public static void deliverLocalBroadcast(String appKey, Packet packet) {
        if (packet == null) {
            return;
        }
        ThreadPoolManager.messageSendExecutor().execute(() -> deliverLocalBroadcastGrouped(appKey, packet));
    }

    /**
     * æ¬æºæç®æ åè¡¨æåºï¼å±äº«æ­£æï¼æ EventLoop ä¸ä»½ cloneï¼ç¦æ­¢è·¨ç¨æ·å¤ç¨å«ç§äºº Target ç Packet èä¸æ¹ Targetã
     */
    public static void deliverLocalFanoutTargets(Packet packet, List<Target> targets) {
        if (packet == null || targets == null || targets.isEmpty()) {
            return;
        }
        ThreadPoolManager.messageSendExecutor().execute(() -> deliverLocalFanoutGrouped(packet, targets));
    }

    private static void deliverLocalFanoutGrouped(Packet packet, List<Target> targets) {
        Map<EventLoop, List<Target>> byLoop = new IdentityHashMap<>();
        List<Target> missed = new ArrayList<>();
        for (Target target : targets) {
            if (target == null || StringUtils.isBlank(target.getTargetIdentity())) {
                continue;
            }
            String combo = IdentityUtil.generalComboIdentity(
                    target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
            ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
            if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
                missed.add(target);
                continue;
            }
            byLoop.computeIfAbsent(ctx.channel().eventLoop(), loop -> new ArrayList<>()).add(target);
        }
        LoginFollowHelper.followMissed(packet, missed);
        if (byLoop.isEmpty()) {
            return;
        }
        for (Map.Entry<EventLoop, List<Target>> entry : byLoop.entrySet()) {
            EventLoop loop = entry.getKey();
            if (loop.isTerminated() || loop.isShutdown() || loop.isShuttingDown()) {
                continue;
            }
            Packet loopPacket = packet.clone();
            List<Target> loopTargets = entry.getValue();
            loop.execute(() -> writeLocalFanoutOnEventLoop(loop, loopPacket, loopTargets, 0));
        }
    }

    private static void writeLocalFanoutOnEventLoop(EventLoop loop, Packet loopPacket,
                                                    List<Target> targets, int from) {
        if (loopPacket.getMessage() == null || loopPacket.getMessage().getMetadata() == null) {
            return;
        }
        int end = Math.min(from + MessageConstant.GROUP_FANOUT_LOCAL_EVENTLOOP_BATCH, targets.size());
        for (int i = from; i < end; i++) {
            Target target = targets.get(i);
            String combo = IdentityUtil.generalComboIdentity(
                    target.getAppKey(), target.getTargetIdentity(), target.getDeviceType());
            ChannelHandlerContext ctx = MessageServerContext.localLoginClientRegisterTable.get(combo);
            if (ctx == null || !MessageSender.isChannelSendable(ctx)) {
                continue;
            }
            Packet outbound = PacketCopyHelper.copyForDelivery(loopPacket, target);
            outbound.getMessage().ensureMetadata().ensureClusterRoute().setFanoutTargets(null);
            if (isRemoteLoginNotify(outbound)) {
                MessageSender.sendControl(ctx, outbound, unused -> {
                    if (ctx.channel() != null && ctx.channel().isActive()) {
                        ctx.close();
                    }
                });
            } else {
                MessageSender.sendControlQuiet(ctx, outbound);
            }
        }
        if (end < targets.size() && !loop.isShuttingDown() && !loop.isShutdown() && !loop.isTerminated()) {
            loop.execute(() -> writeLocalFanoutOnEventLoop(loop, loopPacket, targets, end));
        }
    }

    /**
     * å¼±ä¸è´éåæ¬æºæ³¨åè¡¨ï¼æ EventLoop åæ¡¶åæäº¤ååºãæ¯ä¸ª loop ä¸ä»½ Packet cloneï¼ä¸²è¡ setTargetã
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
                loopPacket.getMessage().ensureMetadata().ensureClusterRoute().setLocalBroadcastOnly(false);
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
     * åä¸ EventLoop ååçååºï¼æ¯æ¹ {@link MessageConstant#IM_LOCAL_BROADCAST_EVENTLOOP_BATCH} æ¡åè®©åº loopã
     * å°½åèä¸ºï¼æ°´ä½é«åè·³è¿ï¼ä¸å SEND_FAILã
     */
    private static void writeLocalBroadcastOnEventLoop(EventLoop loop, Packet loopPacket,
                                                       List<ChannelHandlerContext> ctxs, int from) {
        if (loopPacket.getMessage() == null || loopPacket.getMessage().getMetadata() == null) {
            return;
        }
        int end = Math.min(from + MessageConstant.IM_LOCAL_BROADCAST_EVENTLOOP_BATCH, ctxs.size());
        for (int i = from; i < end; i++) {
            ChannelHandlerContext ctx = ctxs.get(i);
            if (!MessageSender.isChannelSendable(ctx)) {
                continue;
            }
            Packet outbound = PacketCopyHelper.copyForDelivery(loopPacket, null);
            outbound.getMessage().ensureMetadata().ensureClusterRoute().setTarget(null);
            MessageSender.sendControlQuiet(ctx, outbound);
        }
        if (end < ctxs.size() && !loop.isShuttingDown() && !loop.isShutdown() && !loop.isTerminated()) {
            loop.execute(() -> writeLocalBroadcastOnEventLoop(loop, loopPacket, ctxs, end));
        }
    }

    /**
     * éç¥æ¬æºå¨é¨å·²ç»å½å®¢æ·ç«¯ï¼è¯·ä¸»å¨æ­å¼å¹¶éè¿å¶ä»èç¹ã
     * <p>æå¡ç«¯ä¸ close è¿æ¥ï¼ç±å®¢æ·ç«¯æ¶å° {@link MessageTypeEnum#SERVER_NOTIFY} åèªè¡æ­å¼éè¿ã
     *
     * @return æåä¸åéç¥çè¿æ¥æ°
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
        log.warn("notifyAllLocalClientsToReconnect å®æ, notified={}, registryKey={}",
                notified, registrySize);
        return notified;
    }

    /**
     * å¼ºå¶å³é­æ¬æºä»å­æ´»çé¿è¿æ¥ï¼ä»ç¨äºè¿ç¨éåºååºï¼æ¥å¸¸è¿ç»´è¸¢çº¿è¯·ç¨ {@link #notifyAllLocalClientsToReconnect()}ï¼ã
     *
     * @return å°è¯å³é­çè¿æ¥æ°
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
        log.warn("forceCloseAllLocalClients å®æ, attempted={}, registryKey={}", closing.size(), registrySize);
        return closing.size();
    }

    /**
     * åæ¬æºå¨çº¿ä¼è¯åéãè¯·ä¸»å¨éè¿ãç»´æ¤éç¥ï¼ä¸å³é­è¿æ¥ï¼ã
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
                    .build();
            MessageSender.send(notifyPacket, kickTarget);
        } catch (Exception e) {
            log.warn("åéç»´æ¤éè¿éç¥å¤±è´¥ identity={}: {}", loginClientInfo.getIdentity(), e.getMessage());
        }
    }

    private static void awaitChannelsClosed(List<Channel> channels, long timeoutMillis) {
        if (channels.isEmpty()) {
            return;
        }
        long deadline = TimeUtil.currentTimeMillis() + timeoutMillis;
        for (Channel channel : channels) {
            long remain = deadline - TimeUtil.currentTimeMillis();
            if (remain <= 0) {
                break;
            }
            try {
                channel.closeFuture().await(remain, TimeUnit.MILLISECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                log.warn("ç­å¾ channel å³é­è¢«ä¸­æ­");
                break;
            }
        }
    }

    /**
     * è·¨èç¹é¡¶å·éç¥ï¼ååºåå¿é¡»å³ææ¬æºæ§ Channelï¼é¿åå¹½çµå¨çº¿ã
     */
    static boolean isRemoteLoginNotify(Packet packet) {
        return packet != null && packet.getMessage() != null
                && packet.getMessage().getContentType() == MessageContentTypeEnum.REMOTE_LOGIN_CONTENT.getType();
    }
}
