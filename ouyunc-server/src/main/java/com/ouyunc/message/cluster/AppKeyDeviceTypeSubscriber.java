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

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

/**
 * 订阅 appKey / 客户端设备类型变更频道（Redisson Topic）�?
 * <p>payload �?{@link AppKeyDeviceType} / {@link ClientAppKeyDeviceType} �?JSON 字符串�?
 * 发布端请使用 {@code redissonClient.getTopic(channel).publish(JSON.toJSONString(...))}�?/p>
 * <ul>
 *   <li>{@code deviceTypes == null}：忽略本次消�?/li>
 *   <li>{@code deviceTypes} 为空集合：清除定制白名单（appKey 回落全局；客户端回落 appKey/全局�?/li>
 *   <li>非空：覆盖本地缓存（客户端须�?appKey 白名单子集）</li>
 * </ul>
 */
public final class AppKeyDeviceTypeSubscriber {

    private static final Logger log = LoggerFactory.getLogger(AppKeyDeviceTypeSubscriber.class);

    private static volatile Integer appKeyListenerId;
    private static volatile Integer clientListenerId;

    private AppKeyDeviceTypeSubscriber() {
    }

    /**
     * 幂等启动订阅；重复调用直接返回�?
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
            log.info("appKey 设备类型订阅已启�?channels=[{}, {}]",
                    MessageConstant.APP_KEY_PUBLISH_TOPIC, MessageConstant.CLIENT_APP_KEY_PUBLISH_TOPIC);
        } catch (Exception e) {
            log.error("appKey 设备类型订阅失败，将仅依赖启动时 Redis 预热", e);
        }
    }

    /**
     * 停止订阅；在服务停机时调用�?
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
            log.warn("停止客户�?appKey 设备类型订阅异常: {}", e.getMessage());
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
            // null=忽略；空集合=清除定制白名单回落全局；非�?覆盖
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
            String cacheKey = CacheConstant.buildLocalClientInfoCacheKey(
                    clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity());
            // 空集合：清除客户端定制，回落 appKey/全局白名�?
            if (deviceTypes.isEmpty()) {
                MessageServerContext.localClientInfoCache.delete(cacheKey);
                log.info("已清除客户端定制设备类型 appKey={} identity={}",
                        clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity());
                return;
            }
            // JSON/Redis 元素可能�?Integer，统一规范后再校验子集
            Map<Byte, Byte> normalized = DeviceTypeRegistry.toIdentityMap(deviceTypes);
            if (normalized.isEmpty()) {
                log.error("客户端设备类型无有效元素 appKey={} identity={}",
                        clientAppKeyDeviceType.getAppKey(), clientAppKeyDeviceType.getIdentity());
                return;
            }
            for (Byte deviceType : normalized.keySet()) {
                if (!DeviceTypeRegistry.supports(clientAppKeyDeviceType.getAppKey(), deviceType)) {
                    log.error("非法设备类型：{} appKey={}", deviceType, clientAppKeyDeviceType.getAppKey());
                    return;
                }
            }
            MessageServerContext.localClientInfoCache.put(
                    cacheKey,
                    new ClientInfo(
                            clientAppKeyDeviceType.getAppKey(),
                            clientAppKeyDeviceType.getIdentity(),
                            new ArrayList<>(normalized.keySet())));
        } catch (Exception e) {
            log.warn("客户�?appKey 设备类型消息处理失败 payload={}", msg, e);
        }
    }
}
