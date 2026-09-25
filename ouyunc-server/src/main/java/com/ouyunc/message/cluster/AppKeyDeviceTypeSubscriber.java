package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.redisson.api.RTopic;
import org.redisson.api.listener.MessageListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 订阅 appKey 设备白名单与客户端信息变更频道（Redisson Topic）。
 * <p>payload 为 {@link AppKeyDeviceType} / {@link ClientInfo} 的 JSON 字符串。
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
                RTopic clientTopic = MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_INFO_PUBLISH_TOPIC);
                clientListenerId = clientTopic.addListener(String.class, new MessageListener<String>() {
                    @Override
                    public void onMessage(CharSequence channel, String msg) {
                        onClientInfoMessage(msg);
                    }
                });
            }
            log.info("appKey 设备类型订阅已启动 channels=[{}, {}]",
                    MessageConstant.APP_KEY_PUBLISH_TOPIC, MessageConstant.CLIENT_INFO_PUBLISH_TOPIC);
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
                MessageServerContext.redissonClient.getTopic(MessageConstant.CLIENT_INFO_PUBLISH_TOPIC)
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
            if (appKeyDeviceType == null || StringUtils.isBlank(appKeyDeviceType.getAppKey())) {
                return;
            }
            // null=忽略；空集合=清除定制白名单回落全局；非空=覆盖
            Set<Byte> deviceTypes = appKeyDeviceType.getDeviceTypes();
            if (deviceTypes == null) {
                return;
            }
            DeviceTypeRegistry.replaceAppKeyWhitelist(appKeyDeviceType.getAppKey(), deviceTypes);
        } catch (Exception e) {
            log.warn("appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }

    private static void onClientInfoMessage(String msg) {
        if (StringUtils.isBlank(msg)) {
            return;
        }
        try {
            ClientInfo published = JSON.parseObject(msg, ClientInfo.class);
            if (published == null || StringUtils.isAnyBlank(published.getAppKey(), published.getIdentity())) {
                return;
            }
            String appKey = published.getAppKey();
            String identity = published.getIdentity();
            Boolean selfSync = published.getSelfSync();
            Collection<Byte> deviceTypes = published.getSupportDeviceTypes();
            if (deviceTypes == null) {
                if (selfSync != null) {
                    applyClientSettings(appKey, identity, null, selfSync);
                }
                return;
            }
            // 空集合：只清客户端定制设备类型，回落 appKey/全局白名单
            if (deviceTypes.isEmpty()) {
                applyClientSettings(appKey, identity, List.of(), selfSync);
                log.info("已清除客户端定制设备类型 appKey={} identity={}", appKey, identity);
                return;
            }
            // JSON 元素可能是 Integer，统一规范后再校验子集
            Map<Byte, Byte> normalized = DeviceTypeRegistry.toIdentityMap(deviceTypes);
            if (normalized.isEmpty()) {
                log.error("客户端设备类型无有效元素 appKey={} identity={}", appKey, identity);
                return;
            }
            for (Byte deviceType : normalized.keySet()) {
                if (!DeviceTypeRegistry.supports(appKey, deviceType)) {
                    log.error("非法设备类型：{} appKey={}", deviceType, appKey);
                    return;
                }
            }
            applyClientSettings(appKey, identity, List.copyOf(normalized.keySet()), selfSync);
        } catch (Exception e) {
            log.warn("客户端 appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }

    /**
     * 只改已有 {@link ClientInfo}。设备类型为 null 时不动白名单；selfSync 为 null 时不动多端同步。
     * 没有完整客户端信息时不写入半截对象。
     */
    private static void applyClientSettings(String appKey, String identity, Collection<Byte> supportDeviceTypes, Boolean selfSync) {
        if (supportDeviceTypes == null && selfSync == null) {
            return;
        }
        String cacheKey = CacheConstant.buildLocalClientInfoCacheKey(appKey, identity);
        Object cached = MessageServerContext.localClientInfoCache.get(cacheKey);
        if (cached instanceof ClientInfo clientInfo) {
            patchClientInfo(clientInfo, supportDeviceTypes, selfSync);
            return;
        }
        MessageServerContext.evictLocalClientInfo(appKey, identity);
        ClientInfo loaded = MessageServerContext.localClientInfo(appKey, identity);
        if (loaded != null) {
            patchClientInfo(loaded, supportDeviceTypes, selfSync);
            return;
        }
        log.info("本地与 Redis 均无完整客户端信息，忽略客户端热更新 appKey={} identity={}", appKey, identity);
    }

    private static void patchClientInfo(ClientInfo clientInfo, Collection<Byte> supportDeviceTypes, Boolean selfSync) {
        if (supportDeviceTypes != null) {
            clientInfo.setSupportDeviceTypes(supportDeviceTypes);
        }
        if (selfSync != null) {
            clientInfo.setSelfSync(selfSync);
        }
    }
}
