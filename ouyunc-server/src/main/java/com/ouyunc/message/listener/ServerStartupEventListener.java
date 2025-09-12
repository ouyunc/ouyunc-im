package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.SaveModeEnum;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.model.ClientAppKeyDeviceType;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
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
import java.util.stream.Collectors;

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
        Set<String> appKeys = ClientHelper.appKeys();
        if (CollectionUtils.isNotEmpty(appKeys)) {
            // 加载appKey 下的deviceType 配置
            loadAppKeyDeviceTypes(appKeys);
            startAppKeyDeviceTypeSubscription();
            // 启动appKey 下的连接数刷新任务
            startAppKeyConnectionCountRefreshScheduler(appKeys);
        }
        // 启动appKey 下的deviceType 订阅
        // 启动客户端登录信息心跳保活线程
        startClientLoginKeepAliveThread();
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

    /**
     * 这里可以监听appKey级别或者appKey 下某个客户端的设备类型（按道理来讲客户端所能使用的设备类型必须是appKey所支持类型的子集）
     */
    @SuppressWarnings("unchecked")
    private void startAppKeyDeviceTypeSubscription() {
        RedisSerializer<AppKeyDeviceType> appKeyDeviceTypeRedisSerializer = (RedisSerializer<AppKeyDeviceType>) redisTemplate.getValueSerializer();
        RedisSerializer<ClientAppKeyDeviceType> clientAppKeyDeviceTypeRedisSerializer = (RedisSerializer<ClientAppKeyDeviceType>) redisTemplate.getValueSerializer();
        RedisMessageListenerContainer container = createRedisMessageListenerContainer();
        container.setTaskExecutor(Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "redis-pubsub-listener");
            t.setDaemon(true);
            return t;
        }));
        // 监听appKey下的设备类型
        container.addMessageListener((message, pattern) -> {
            AppKeyDeviceType appKeyDeviceType = appKeyDeviceTypeRedisSerializer.deserialize(message.getBody());
            if (appKeyDeviceType != null) {
                log.info("正在处理appKey下的设备类型 {} ...", appKeyDeviceType);
                MessageServerContext.addAppKeyDeviceType(appKeyDeviceType.getAppKey(), appKeyDeviceType.getDeviceTypes());
            }
            // 添加进入appKey对应的设备类型集合中
        }, new ChannelTopic(MessageConstant.APP_KEY_PUBLISH_TOPIC));
        // 监听单个appKey下的某个客户端所支持的设备类型
        container.addMessageListener((message, pattern) -> {
            ClientAppKeyDeviceType clientAppKeyDeviceType = clientAppKeyDeviceTypeRedisSerializer.deserialize(message.getBody());
            if (clientAppKeyDeviceType != null) {
                log.info("正在处理appKey下的客户端所支持的设备类型 {} ...", clientAppKeyDeviceType);
                //  添加进入appKey对应的设备类型集合中,是否需要主动关闭相关链接？还是在发送消息鉴权的时候进行校验,被动关闭吧
                // 需要校验下，单独设置的必须要再appKey下的设备类型集合中
                Set<DeviceType> deviceTypes = clientAppKeyDeviceType.getDeviceTypes();
                if (CollectionUtils.isNotEmpty(deviceTypes)) {
                    boolean flag = true;
                    for (DeviceType deviceType : deviceTypes) {
                        if (!MessageServerContext.deviceTypeList(clientAppKeyDeviceType.getAppKey()).contains(deviceType)) {
                            log.error("非法设备类型：{}", deviceType);
                            flag = false;
                            break;
                        }
                    }
                    if (flag) {
                        log.info("{} 添加进入appKey对应的设备类型集合中", deviceTypes);
                        MessageServerContext.localClientInfoCache.put(clientAppKeyDeviceType.getAppKey() + CacheConstant.COLON + clientAppKeyDeviceType.getIdentity(), new ClientInfo(clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity(), clientAppKeyDeviceType.getDeviceTypes().stream().map(DeviceType::getDeviceTypeValue).collect(Collectors.toList())));
                    }
                }
            }
            // 添加进入appKey对应的设备类型集合中
        }, new ChannelTopic(MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC));
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
