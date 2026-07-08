package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.OnlineEnum;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.entity.AppEntity;
import com.ouyunc.message.context.AppKeyConnectionCleanupRegistry;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import org.apache.commons.collections4.CollectionUtils;
import org.jetbrains.annotations.NotNull;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

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
                    MessageServerContext.localLoginClientRegisterTable.put(comboIdentity, ctx);
                }, ThreadPoolManager.messageProcessorExecutor())
                .thenCompose(unused -> runOnEventLoop(channel, () -> {
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN, loginClientInfo);
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_HEARTBEAT_TIMEOUT,
                            loginClientInfo.getHeartBeatTimeout());
                    ChannelAttrUtil.setChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LAST_HEARTBEAT_TIMESTAMP,
                            loginClientInfo.getLastLoginTime());
                }))
                .whenComplete((unused, ex) -> {
                    if (ex != null) {
                        MessageServerContext.localLoginClientRegisterTable.delete(comboIdentity);
                    }
                });
    }

    public static void unbindLocalRegisterTable(LoginClientInfo loginClientInfo) {
        String comboIdentity = IdentityUtil.generalComboIdentity(
                loginClientInfo.getAppKey(), loginClientInfo.getIdentity(), loginClientInfo.getDeviceType());
        MessageServerContext.localLoginClientRegisterTable.delete(comboIdentity);
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

    private static void doBindRemote(LoginClientInfo loginClientInfo, String comboIdentity) {
        RLock lock = MessageServerContext.redissonClient.getLock(
                CacheConstant.buildIdentityBindOrUnbindLockCacheKey(loginClientInfo.getAppKey(), comboIdentity));
        try {
            if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                try {
                    redisTemplate.executePipelined(new SessionCallback<>() {
                        @SuppressWarnings("unchecked")
                        @Override
                        public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                            String loginCacheKey = CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity);
                            long loginExpireTime = loginClientInfo.getLoginExpireTime();
                            String appKeyConnectionsCacheKey = CacheConstant.buildConnectionsCacheKey(loginClientInfo.getAppKey());
                            if (loginExpireTime <= 0) {
                                operations.opsForValue().set((K) loginCacheKey, (V) loginClientInfo);
                                operations.opsForZSet().add((K) (appKeyConnectionsCacheKey), (V) comboIdentity, NumberConstant.NUMBER_NEGATIVE_1);
                            } else {
                                operations.opsForValue().set((K) loginCacheKey, (V) loginClientInfo, loginExpireTime, TimeUnit.SECONDS);
                                operations.opsForZSet().add((K) (appKeyConnectionsCacheKey), (V) comboIdentity, TimeUtil.currentTimeMillis() + loginExpireTime * MessageConstant.NUMBER_1000);
                            }
                            return null;
                        }
                    });
                    AppKeyConnectionCleanupRegistry.track(loginClientInfo.getAppKey());
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


    /**
     * 计算客户端登录过期时间
     * @param heartBeatExpireTime
     * @return
     */
    public static long calculateClientLoginExpireTime(int heartBeatExpireTime) {
        long expireTime = NumberConstant.NUMBER_NEGATIVE_1;
        // 计算心跳超时时间
        int heartBeatTimeout = calculateClientHeartBeatTimeout(heartBeatExpireTime);
        // 如果客户端的登录信息存储模式是有限/短暂的则 保存时间是，心跳间隔时间*最大重试次数+5，这里加5是为了尽可能给其他程序去处理相关逻辑，如读写空闲事件
        if (MessageServerContext.serverProperties().isClientHeartBeatEnable() && SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode())) {
            expireTime = Integer.toUnsignedLong((heartBeatTimeout * MessageServerContext.serverProperties().getClientHeartBeatWaitRetry())) + NumberConstant.NUMBER_5;
        }
        return expireTime;
    }

    public static Map<String, List<LoginClientInfo>> onlineAllBatch(String appKey, Set<String> identities) {
        Map<String, List<LoginClientInfo>> result = new HashMap<>(identities.size());
        Set<String> remoteKeys = new HashSet<>();

        // Phase 1: 本地注册表查询（零网络开销）
        for (String identity : identities) {
            List<LoginClientInfo> localHits = new ArrayList<>();
            Collection<Byte> deviceTypes = MessageServerContext.deviceTypeList(appKey, identity);
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
                remoteKeys.add(CacheConstant.buildLoginCacheKey(appKey, comboId));
            }
            if (!localHits.isEmpty()) {
                result.put(identity, localHits);
            }
        }

        // Phase 2: 未命中本地的 → 一次 MGET 批量查 Redis
        if (!remoteKeys.isEmpty()) {
            List<String> keyList = new ArrayList<>(remoteKeys);
            List<Object> cached = redisTemplate.opsForValue().multiGet(keyList);
            if (cached != null) {
                for (int i = 0; i < cached.size(); i++) {
                    if (cached.get(i) instanceof LoginClientInfo info
                            && OnlineEnum.ONLINE.equals(info.getOnlineStatus())) {
                        result.computeIfAbsent(info.getIdentity(), k -> new ArrayList<>()).add(info);
                    }
                }
            }
        }
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
        // 判断comboIdentitySet的size 与结果集的大小是否相等，如果不相等则在从redis获取，如果相等则返回
        // 从ctx上下文获取客户端登录信息
        allLoginClientChannelHandlerContexts.forEach(ctx -> {
            LoginClientInfo loginClientInfo = ChannelAttrUtil.getChannelAttribute(ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
            if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
                loginClientInfoList.add(loginClientInfo);
                // 移除掉已经从本地获取的有效客户端登录信息
                comboIdentitySet.remove(IdentityUtil.generalComboIdentity(appKey, identity, loginClientInfo.getDeviceType()));
            }
        });
        if (comboIdentitySet.size() == loginClientInfoList.size()) {
            return loginClientInfoList;
        }
        // 如果不相等，则将没有查询到的数据通过缓存来获取
        // comboIdentitySet 中排除已经从本地获取结果的key
        Set<String> remoteLoginClientIdentitySet = comboIdentitySet.parallelStream().map(comboIdentity -> CacheConstant.buildLoginCacheKey(appKey, comboIdentity)).collect(Collectors.toSet());
        Collection<LoginClientInfo> remoteCacheLoginClientInfos = MessageServerContext.remoteLoginClientInfoCache.getAll(remoteLoginClientIdentitySet);
        if (CollectionUtils.isNotEmpty(remoteCacheLoginClientInfos)) {
             // 筛选合法的数据
            loginClientInfoList.addAll(remoteCacheLoginClientInfos.stream().filter(loginClientInfo -> loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())).toList());
        }
        // 最后返回符合条件的数据
        return loginClientInfoList;
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
        if (loginClientInfo != null && OnlineEnum.ONLINE.equals(loginClientInfo.getOnlineStatus())) {
            return loginClientInfo;
        }
        return null;
    }


    /**
     * 获取某个在线appKey 连接数
     * @param appKey
     * @return
     */
    public static long connections(String appKey) {
        long now = TimeUtil.currentTimeMillis();
        Long connections = redisTemplate.opsForZSet().count(CacheConstant.buildConnectionsCacheKey(appKey), now, Double.POSITIVE_INFINITY);
        if (connections == null) {
            return NumberConstant.NUMBER_0;
        }
        return connections;
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
     * 获取所有appKey的连接数总和
     * @return
     */
    @SuppressWarnings("unchecked")
    public static long connections() {
        Set<String> appKeys = appKeys();
        if (CollectionUtils.isEmpty(appKeys)) {
            return NumberConstant.NUMBER_0;
        }
        // 依次获取每个appKey的数据
        long totalConnections = NumberConstant.NUMBER_0;
        long now = TimeUtil.currentTimeMillis();
        List<Object> executedResultList = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(@NotNull RedisOperations<K, V> operations) throws DataAccessException {
                for (String appKey : appKeys) {
                    operations.opsForZSet().count((K) CacheConstant.buildConnectionsCacheKey(appKey), now, Double.POSITIVE_INFINITY);
                }
                return null;
            }
        });
        // 统计所有appKey的连接数
        if (CollectionUtils.isNotEmpty(executedResultList)) {
            for (Object result : executedResultList) {
                if (result instanceof Long connection) {
                    totalConnections += connection;
                }
            }
        }
        return totalConnections;
    }
}
