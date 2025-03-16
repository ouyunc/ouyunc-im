package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.schedule.ScheduleTimer;
import com.ouyunc.message.thread.LoginKeepAliveThread;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author fzx
 * @description 服务启动成功事件
 */
public class ServerStartupEventListener implements MessageListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupEventListener.class);

    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

    /**
     * 服务启动成功事件,
     * @param event
     */
    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        Set<String> appKeys = fetchAppKeys();
        if (CollectionUtils.isNotEmpty(appKeys)) {
            // 加载appKey 下的deviceType 配置
            loadAppKeyDeviceTypes(appKeys);
            // 启动appKey 下的连接数刷新任务
            startAppKeyConnectionCountRefreshScheduler(appKeys);
        }
        // 启动appKey 下的deviceType 订阅
        // 启动客户端登录信息心跳保活线程
        startClientLoginKeepAliveThread();
    }
    @SuppressWarnings("unchecked")
    private Set<String> fetchAppKeys() {
        return (Set<String>) redisTemplate.opsForSet().members(CacheConstant.OUYUNC + CacheConstant.APP_KEYS);
    }

    @SuppressWarnings("unchecked")
    private void loadAppKeyDeviceTypes(Set<String> appKeys) {
        for (String appKey : appKeys) {
            Set<DeviceType> deviceTypeSet = (Set<DeviceType>) redisTemplate.opsForSet().members(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.DEVICE_TYPE);
            if (CollectionUtils.isNotEmpty(deviceTypeSet)) {
                MessageServerContext.addAppKeyDeviceType(appKey, deviceTypeSet);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private void startAppKeyDeviceTypeSubscription() {
        RedisSerializer<AppKeyDeviceType> valueSerializer = (RedisSerializer<AppKeyDeviceType>) redisTemplate.getValueSerializer();
        RedisMessageListenerContainer container = createRedisMessageListenerContainer();
        container.setTaskExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "redis-pubsub-listener");
            t.setDaemon(true);
            return t;
        }));
        container.addMessageListener((message, pattern) -> {
            AppKeyDeviceType appKeyDeviceType = valueSerializer.deserialize(message.getBody());
            if (appKeyDeviceType != null) {
                MessageServerContext.addAppKeyDeviceType(appKeyDeviceType.getAppKey(), appKeyDeviceType.getDeviceTypes());
            }
            // 添加进入appKey对应的设备类型集合中
        }, new ChannelTopic(MessageConstant.APP_KEY_PUBLISH_TOPIC));
        // 一定不要忘记了这句
        container.afterPropertiesSet();
        container.start();
    }

    private RedisMessageListenerContainer createRedisMessageListenerContainer() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(Objects.requireNonNull(redisTemplate.getConnectionFactory()));
        return container;
    }

    private void startClientLoginKeepAliveThread() {
        if (isClientHeartBeatEnabled()) {
            Thread clientLoginKeepAliveThread = new Thread(new LoginKeepAliveThread());
            clientLoginKeepAliveThread.setName("client-login-keep-alive-thread");
            clientLoginKeepAliveThread.setDaemon(true);
            clientLoginKeepAliveThread.start();
        }
    }

    private boolean isClientHeartBeatEnabled() {
        return MessageServerContext.serverProperties().isClientHeartBeatEnable() &&
                SaveModeEnum.FINITE.equals(MessageServerContext.serverProperties().getClientLoginInfoSaveMode());
    }

    private void startAppKeyConnectionCountRefreshScheduler(Set<String> appKeys) {
        if (isAppKeyConnectionCountRefreshEnabled()) {
            RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
            AtomicLong minScore = new AtomicLong(NumberConstant.NUMBER_0);
            ScheduleTimer.scheduleAtFixedRate("appKey-connection-count-refresh-timer", (taskWrapper) -> {
                if (CollectionUtils.isNotEmpty(appKeys)) {
                    long maxScore = TimeUtil.currentTimeMillis();
                    if (minScore.get() == NumberConstant.NUMBER_0 || minScore.get() >= maxScore) {
                        minScore.set(maxScore - MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshOffset() * MessageConstant.SECOND_TIMESTAMP);
                    }
                    for (String appKey : appKeys) {
                        // 如果开启集群模式,则加锁保证进程安全
                        if (MessageServerContext.serverProperties().isClusterEnable()) {
                            RLock lock = MessageServerContext.redissonClient.getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.APP_KEY + appKey);
                            try {
                                if (lock.tryLock(MessageConstant.LOCK_WAIT_TIME, MessageConstant.LOCK_LEASE_TIME, TimeUnit.SECONDS)) {
                                    refreshAppKeyConnectionCount(redisTemplate, appKey, minScore, maxScore);
                                }
                            } catch (InterruptedException e) {
                                log.error("appKey-connection-count-refresh-timer 获取锁失败,原因：{}", e.getMessage());
                            } finally {
                                if (lock.isHeldByCurrentThread()) {
                                    lock.unlock();
                                }
                            }
                        }else {
                            refreshAppKeyConnectionCount(redisTemplate, appKey, minScore, maxScore);
                        }
                    }
                }
            }, getRefreshInterval(), getRefreshInterval(), TimeUnit.SECONDS);
        }
    }

    private boolean isAppKeyConnectionCountRefreshEnabled() {
        return MessageServerContext.serverProperties().isAppKeyConnectionCountRefreshEnable();
    }

    private long getRefreshInterval() {
        return MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshInterval();
    }

    private void refreshAppKeyConnectionCount(RedisTemplate<String, Object> redisTemplate, String appKey, AtomicLong minScore, long maxScore) {
        while (minScore.get() < maxScore) {
            long currentMaxScore = Math.min(minScore.get() + MessageServerContext.serverProperties().getAppKeyConnectionCountRefreshStep(), maxScore);
            redisTemplate.opsForZSet().removeRangeByScore(
                    CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.CONNECTIONS,
                    minScore.get(), currentMaxScore);
            minScore.set(currentMaxScore);
        }
    }


}
