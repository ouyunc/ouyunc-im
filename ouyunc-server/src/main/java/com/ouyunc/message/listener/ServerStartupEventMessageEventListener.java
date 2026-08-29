package com.ouyunc.message.listener;

import com.google.common.collect.Lists;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.model.ClientAppKeyDeviceType;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.AppKeyConnectionCleanupRegistry;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.http.HttpRequestDispatcher;
import com.ouyunc.message.monitor.MonitorInitializer;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.schedule.TimerTaskWrapper;
import com.ouyunc.repository.DefaultRepository;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

/**
 * @author fzx
 * @description 服务启动成功事件
 */
@EventListener
class ServerStartupEventMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupEventMessageEventListener.class);

    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

    private static final AtomicLong APP_KEY_CONNECTION_REFRESH_RUN = new AtomicLong();

    private static final AtomicBoolean APP_KEY_DEVICE_TYPE_SUBSCRIPTION_STARTED = new AtomicBoolean(false);

    private static final AtomicBoolean APP_KEY_CONNECTION_SCHEDULER_STARTED = new AtomicBoolean(false);

    private static final AtomicBoolean RUNTIME_RESOURCES_SHUTDOWN_DONE = new AtomicBoolean(false);

    private static volatile RedisMessageListenerContainer appKeyDeviceTypeListenerContainer;

    /**
     * 服务启动成功事件,
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_STARTUP;
    }

    @Override
    public void onEvent(MessageEvent event) {
        HttpRequestDispatcher.logRegisteredHttpRoutesOnStartup();
        AppKeyConnectionCleanupRegistry.initFromRedis();
        // 先从 ouyunc_im_app 预热 Redis Hash，避免 Redis 空缓存时登录全部报 appKey 不存在
        List<String> warmedAppKeys;
        try {
            warmedAppKeys = DefaultRepository.INSTANCE.warmupAppKeys();
        } catch (Exception e) {
            log.error("预热 app-keys 失败", e);
            warmedAppKeys = List.of();
        }
        Set<String> appKeys;
        try {
            appKeys = ClientHelper.appKeys();
        } catch (Exception e) {
            log.error("启动读取 Redis app-keys 失败，使用预热列表兜底", e);
            appKeys = Set.of();
        }
        if (CollectionUtils.isEmpty(appKeys) && CollectionUtils.isNotEmpty(warmedAppKeys)) {
            appKeys = Set.copyOf(warmedAppKeys);
        }
        if (CollectionUtils.isNotEmpty(appKeys)) {
            // 加载appKey 下的deviceType 配置
            loadAppKeyDeviceTypes(Lists.newArrayList(appKeys));
        }
        // 无论当前 Redis 是否已有 appKey，均启动订阅，避免启动时空集合导致后续无法收到设备类型/增量 track
        startAppKeyDeviceTypeSubscription();
        // 使用可增量更新的 appKey 集合，避免仅依赖启动快照导致新 appKey 不参与 ZSet 清理
        startAppKeyConnectionCountRefreshScheduler();
        // 启动资源监控
        MonitorInitializer.startMonitoring();
    }

    @SuppressWarnings("unchecked")
    private void loadAppKeyDeviceTypes(List<String> appKeys) {
        List<Object> results = redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (String appKey : appKeys) {
                    operations.opsForSet().members((K) CacheConstant.buildAppKeyDeviceTypeCacheKey(appKey));
                }
                return null;
            }
        });
        for (int i = 0; i < appKeys.size(); i++) {
            String appKey = appKeys.get(i);
            Set<Byte> deviceTypeSet = (Set<Byte>) results.get(i);
            if (CollectionUtils.isNotEmpty(deviceTypeSet)) {
                MessageServerContext.addAppKeyDeviceType(appKey, deviceTypeSet);
            }
        }
    }

    /**
     * 释放 appKey 设备类型订阅与连接数清理定时任务；在 {@link MessageEventTypeEnum#SERVER_STOP} 中同步调用。
     */
    static void shutdownRuntimeResources() {
        if (!RUNTIME_RESOURCES_SHUTDOWN_DONE.compareAndSet(false, true)) {
            return;
        }
        RedisMessageListenerContainer container = appKeyDeviceTypeListenerContainer;
        appKeyDeviceTypeListenerContainer = null;
        if (container != null) {
            try {
                container.stop();
            } catch (Exception e) {
                log.warn("停止 appKey 设备类型 Redis 订阅容器异常: {}", e.getMessage());
            }
            try {
                container.destroy();
            } catch (Exception e) {
                log.warn("销毁 appKey 设备类型 Redis 订阅容器异常: {}", e.getMessage());
            }
        }
        TimerTaskWrapper task = TimerTaskWrapper.timerTaskCaffeine.get(MessageConstant.APP_KEY_CONNECTION_COUNT_REFRESH_TASK_ID);
        if (task != null) {
            task.cancel();
        }
    }

    /**
     * 这里可以监听appKey级别或者appKey 下某个客户端的设备类型（按道理来讲客户端所能使用的设备类型必须是appKey所支持类型的子集）
     */
    private void startAppKeyDeviceTypeSubscription() {
        if (!APP_KEY_DEVICE_TYPE_SUBSCRIPTION_STARTED.compareAndSet(false, true)) {
            log.debug("appKey 设备类型 Redis 订阅已初始化，跳过重复启动");
            return;
        }
        try {
            doStartAppKeyDeviceTypeSubscription();
        } catch (RuntimeException e) {
            APP_KEY_DEVICE_TYPE_SUBSCRIPTION_STARTED.set(false);
            throw e;
        }
    }

    @SuppressWarnings("unchecked")
    private void doStartAppKeyDeviceTypeSubscription() {
        RedisSerializer<AppKeyDeviceType> appKeyDeviceTypeRedisSerializer = (RedisSerializer<AppKeyDeviceType>) redisTemplate.getValueSerializer();
        RedisSerializer<ClientAppKeyDeviceType> clientAppKeyDeviceTypeRedisSerializer = (RedisSerializer<ClientAppKeyDeviceType>) redisTemplate.getValueSerializer();
        RedisMessageListenerContainer container = createRedisMessageListenerContainer();
        container.setTaskExecutor(ThreadPoolManager.redisPubSubExecutor());
        // 监听appKey下的设备类型
        container.addMessageListener((message, pattern) -> {
            AppKeyDeviceType appKeyDeviceType = appKeyDeviceTypeRedisSerializer.deserialize(message.getBody());
            if (appKeyDeviceType != null) {
                AppKeyConnectionCleanupRegistry.track(appKeyDeviceType.getAppKey());
                MessageServerContext.addAppKeyDeviceType(appKeyDeviceType.getAppKey(), appKeyDeviceType.getDeviceTypes());
            }
            // 添加进入appKey对应的设备类型集合中
        }, new ChannelTopic(MessageConstant.APP_KEY_PUBLISH_TOPIC));
        // 监听单个appKey下的某个客户端所支持的设备类型
        container.addMessageListener((message, pattern) -> {
            ClientAppKeyDeviceType clientAppKeyDeviceType = clientAppKeyDeviceTypeRedisSerializer.deserialize(message.getBody());
            if (clientAppKeyDeviceType != null) {
                AppKeyConnectionCleanupRegistry.track(clientAppKeyDeviceType.getAppKey());
                //  添加进入appKey对应的设备类型集合中,是否需要主动关闭相关链接？还是在发送消息鉴权的时候进行校验,被动关闭吧
                // 需要校验下，单独设置的必须要再appKey下的设备类型集合中
                Set<Byte> deviceTypes = clientAppKeyDeviceType.getDeviceTypes();
                if (CollectionUtils.isNotEmpty(deviceTypes)) {
                    boolean flag = true;
                    for (Byte deviceType : deviceTypes) {
                        if (!MessageServerContext.deviceTypeList(clientAppKeyDeviceType.getAppKey()).contains(deviceType)) {
                            log.error("非法设备类型：{}", deviceType);
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        MessageServerContext.localClientInfoCache.put(CacheConstant.buildLocalClientInfoCacheKey(clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity()), new ClientInfo(clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity(), clientAppKeyDeviceType.getDeviceTypes().stream().filter(Objects::nonNull).collect(Collectors.toList())));
                    }
                }
            }
            // 添加进入appKey对应的设备类型集合中
        }, new ChannelTopic(MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC));
        // 一定不要忘记了这句
        container.afterPropertiesSet();
        container.start();
        appKeyDeviceTypeListenerContainer = container;
    }

    private RedisMessageListenerContainer createRedisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(Objects.requireNonNull(redisTemplate.getConnectionFactory()));
        return container;
    }

    private void startAppKeyConnectionCountRefreshScheduler() {
        if (!isAppKeyConnectionCountRefreshEnabled()) {
            return;
        }
        if (!APP_KEY_CONNECTION_SCHEDULER_STARTED.compareAndSet(false, true)) {
            log.debug("appKey 连接数 ZSet 清理定时任务已初始化，跳过重复启动");
            return;
        }
        RedisTemplate<String, Object> connectionCountRedis = CacheFactory.REDIS.instance();
        try {
            ScheduleTimer.scheduleAtFixedRate(MessageConstant.APP_KEY_CONNECTION_COUNT_REFRESH_TASK_ID, (taskWrapper) -> {
            int fullSyncEvery = MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshFullSyncEveryRuns();
            if (fullSyncEvery > 0) {
                long run = APP_KEY_CONNECTION_REFRESH_RUN.incrementAndGet();
                if (run % fullSyncEvery == 0) {
                    AppKeyConnectionCleanupRegistry.mergeAllFromRedis();
                }
            }
            if (AppKeyConnectionCleanupRegistry.isEmpty()) {
                return;
            }
            long maxScore = TimeUtil.currentTimeMillis();
            AppKeyConnectionCleanupRegistry.eachTracked(appKey -> {
                if (MessageServerContext.serverProperties().isClusterEnable()) {
                    RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.buildAppKeyLockCacheKey(appKey));
                    try {
                        if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                            refreshAppKeyConnectionCount(connectionCountRedis, appKey, maxScore);
                        }
                    } catch (InterruptedException e) {
                        log.error("{} 获取锁失败,原因：{}", MessageConstant.APP_KEY_CONNECTION_COUNT_REFRESH_TASK_ID, e.getMessage());
                        Thread.currentThread().interrupt();
                    } finally {
                        if (lock.isHeldByCurrentThread()) {
                            lock.unlock();
                        }
                    }
                } else {
                    refreshAppKeyConnectionCount(connectionCountRedis, appKey, maxScore);
                }
            });
        }, getRefreshInterval(), getRefreshInterval(), TimeUnit.SECONDS);
        } catch (RuntimeException e) {
            APP_KEY_CONNECTION_SCHEDULER_STARTED.set(false);
            throw e;
        }
    }

    private boolean isAppKeyConnectionCountRefreshEnabled() {
        return MessageServerContext.serverProperties().isAppKeyConnectionCountRefreshEnable();
    }

    private long getRefreshInterval() {
        return MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshInterval();
    }

    private void refreshAppKeyConnectionCount(RedisTemplate<String, Object> redisTemplate, String appKey, long nowScore) {
        String connectionsCacheKey = CacheConstant.buildConnectionsCacheKey(appKey);
        int batchSize = (int) Math.max(1L, MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshStep());
        int maxBatchesPerRun = Math.max(1, MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshMaxBatchesPerRun());
        for (int i = 0; i < maxBatchesPerRun; i++) {
            Set<Object> expiredMembers = redisTemplate.opsForZSet().rangeByScore(connectionsCacheKey, Double.NEGATIVE_INFINITY, nowScore, 0, batchSize);
            if (CollectionUtils.isEmpty(expiredMembers)) {
                break;
            }
            redisTemplate.opsForZSet().remove(connectionsCacheKey, expiredMembers.toArray());
            if (expiredMembers.size() < batchSize) {
                break;
            }
        }
    }


}
