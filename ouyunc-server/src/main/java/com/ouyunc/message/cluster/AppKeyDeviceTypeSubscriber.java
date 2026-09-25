package com.ouyunc.message.cluster;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.AppKeyDeviceType;
import com.ouyunc.base.model.ClientAppKeyDeviceType;
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
            Set<Byte> deviceTypes = clientAppKeyDeviceType.getDeviceTypes();
            if (deviceTypes == null) {
                return;
            }
            String appKey = clientAppKeyDeviceType.getAppKey();
            String identity = clientAppKeyDeviceType.getIdentity();
            // 空集合：只清客户端定制设备类型，回落 appKey/全局白名单
            if (deviceTypes.isEmpty()) {
                applyClientSupportDeviceTypes(appKey, identity, List.of());
                log.info("已清除客户端定制设备类型 appKey={} identity={}", appKey, identity);
                return;
            }
            // JSON/Redis 元素可能是 Integer，统一规范后再校验子集
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
            applyClientSupportDeviceTypes(appKey, identity, List.copyOf(normalized.keySet()));
        } catch (Exception e) {
            log.warn("客户端 appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }

    /**
     * 只改已有 {@link ClientInfo} 的设备白名单，保留 selfSync 等字段。
     * 本地未命中时按原路径加载 Redis；没有完整客户端信息时不写入半截对象，避免挡住后续加载。
     */
    private static void applyClientSupportDeviceTypes(String appKey, String identity, Collection<Byte> supportDeviceTypes) {
        String cacheKey = CacheConstant.buildLocalClientInfoCacheKey(appKey, identity);
        Object cached = MessageServerContext.localClientInfoCache.get(cacheKey);
        if (cached instanceof ClientInfo clientInfo) {
            clientInfo.setSupportDeviceTypes(supportDeviceTypes);
            return;
        }
        ClientInfo loaded = MessageServerContext.localClientInfo(appKey, identity);
        if (loaded != null) {
            loaded.setSupportDeviceTypes(supportDeviceTypes);
            return;
        }
        log.info("本地与 Redis 均无完整客户端信息，忽略设备类型热更新 appKey={} identity={}", appKey, identity);
    }
}
