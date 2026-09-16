package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.model.ClientAppKeyDeviceType;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 订阅 appKey / 客户端设备类型变更频道（Redisson Topic）。
 * <p>payload 为 {@link AppKeyDeviceType} / {@link ClientAppKeyDeviceType} 的 JSON 字符串。
 * 发布端请使用 {@code redissonClient.getTopic(channel).publish(JSON.toJSONString(...))}。</p>
 */
public final class AppKeyDeviceTypeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AppKeyDeviceTypeSubscriber.class);

    private static volatile Integer appKeyListenerId;
    private static volatile Integer clientListenerId;

    private AppKeyDeviceTypeSubscriber() {
    }

    /**
     * 幂等启动订阅；重复调用直接返回。
     */
    public static synchronized void start() {
        if (appKeyListenerId != null && clientListenerId != null) {
            return;
        }
        try {
            if (appKeyListenerId == null) {
                RTopic appKeyTopic = MessageServerContext.redissonClient.getTopic(MessageConstant.APP_KEY_PUBLISH_TOPIC);
                appKeyListenerId = appKeyTopic.addListener(String.class, new MessageListener<String>() {
                    @Override
                    public void onMessage(CharSequence channel, String msg) {
                        onAppKeyDeviceTypeMessage(msg);
                    }
                });
            }
            if (clientListenerId == null) {
                RTopic clientTopic = MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC);
                clientListenerId = clientTopic.addListener(String.class, new MessageListener<String>() {
                    @Override
                    public void onMessage(CharSequence channel, String msg) {
                        onClientAppKeyDeviceTypeMessage(msg);
                    }
                });
            }
            log.info("appKey 设备类型订阅已启动 channels=[{}, {}]",
                    MessageConstant.APP_KEY_PUBLISH_TOPIC, MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC);
        } catch (Exception e) {
            log.error("appKey 设备类型订阅失败，将仅依赖启动时 Redis 预热", e);
        }
    }

    /**
     * 停止订阅；在服务停机时调用。
     */
    public static synchronized void stop() {
        Integer appId = appKeyListenerId;
        Integer clientId = clientListenerId;
        appKeyListenerId = null;
        clientListenerId = null;
        try {
            if (appId != null) {
                MessageServerContext.redissonClient.getTopic(MessageConstant.APP_KEY_PUBLISH_TOPIC)
                        .removeListener(appId);
            }
        } catch (Exception e) {
            log.warn("停止 appKey 设备类型订阅异常: {}", e.getMessage());
        }
        try {
            if (clientId != null) {
                MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC)
                        .removeListener(clientId);
            }
        } catch (Exception e) {
            log.warn("停止客户端 appKey 设备类型订阅异常: {}", e.getMessage());
        }
    }

    private static void onAppKeyDeviceTypeMessage(String msg) {
        if (StringUtils.isBlank(msg)) {
            return;
        }
        try {
            AppKeyDeviceType appKeyDeviceType = JSON.parseObject(msg, AppKeyDeviceType.class);
            if (appKeyDeviceType != null && StringUtils.isNotBlank(appKeyDeviceType.getAppKey())) {
                MessageServerContext.addAppKeyDeviceType(appKeyDeviceType.getAppKey(), appKeyDeviceType.getDeviceTypes());
            }
        } catch (Exception e) {
            log.warn("appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }

    private static void onClientAppKeyDeviceTypeMessage(String msg) {
        if (StringUtils.isBlank(msg)) {
            return;
        }
        try {
            ClientAppKeyDeviceType clientAppKeyDeviceType = JSON.parseObject(msg, ClientAppKeyDeviceType.class);
            if (clientAppKeyDeviceType == null || StringUtils.isAnyBlank(
                    clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity())) {
                return;
            }
            // 客户端设备类型必须是 appKey 已支持类型的子集
            Set<Byte> deviceTypes = clientAppKeyDeviceType.getDeviceTypes();
            if (CollectionUtils.isEmpty(deviceTypes)) {
                return;
            }
            for (Byte deviceType : deviceTypes) {
                if (!MessageServerContext.deviceTypeList(clientAppKeyDeviceType.getAppKey()).contains(deviceType)) {
                    log.error("非法设备类型：{}", deviceType);
                    return;
                }
            }
            MessageServerContext.localClientInfoCache.put(
                    CacheConstant.buildLocalClientInfoCacheKey(
                            clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity()),
                    new ClientInfo(
                            clientAppKeyDeviceType.getAppKey(),
                            clientAppKeyDeviceType.getIdentity(),
                            deviceTypes.stream().filter(Objects::nonNull).collect(Collectors.toList())));
        } catch (Exception e) {
            log.warn("客户端 appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }
}
