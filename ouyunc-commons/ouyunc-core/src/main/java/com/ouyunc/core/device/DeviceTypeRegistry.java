package com.ouyunc.core.device;

import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * 设备类型白名单注册表：全局默认 + appKey 定制 + identity 子集。
 * <p>启动时 {@link #registerDefaults(Class)} 注入全局枚举；运行期由 Redis 预热 / Topic 热更新
 * 调用 {@link #replaceAppKeyWhitelist(String, Collection)}。查询一律软失败（不抛异常）。</p>
 */
public final class DeviceTypeRegistry {

    private static final Logger log = LoggerFactory.getLogger(DeviceTypeRegistry.class);

    /**
     * 全局默认设备类型（来自 {@link DeviceType} 枚举注册）。
     */
    private static final Cache<Byte, Byte> DEFAULT_CACHE = new CaffeineLocalCache<>(
            "defaultDeviceTypeCache",
            Caffeine.newBuilder().build(new CacheLoader<>() {
                @Override
                public @Nullable Byte load(Byte key) {
                    return null;
                }
            }));

    /**
     * appKey → 定制设备类型白名单（恒等 Map）。无条目时回落 {@link #DEFAULT_CACHE}。
     */
    private static final Cache<String, Map<Byte, Byte>> APP_KEY_CACHE = new CaffeineLocalCache<>(
            "appKeyDeviceTypeCache",
            Caffeine.newBuilder().build(new CacheLoader<>() {
                @Override
                public @Nullable Map<Byte, Byte> load(String key) {
                    return null;
                }
            }));

    private DeviceTypeRegistry() {
    }

    /**
     * 注册全局默认设备类型枚举（服务启动时调用一次）。
     */
    public static void registerDefaults(Class<? extends DeviceType> deviceTypeClass) {
        if (deviceTypeClass == null || !deviceTypeClass.isEnum()) {
            return;
        }
        DeviceType[] constants = deviceTypeClass.getEnumConstants();
        if (constants == null) {
            return;
        }
        for (DeviceType deviceType : constants) {
            if (deviceType != null && deviceType.getType() != null) {
                DEFAULT_CACHE.put(deviceType.getType(), deviceType.getType());
            }
        }
    }

    /**
     * 覆盖或清除 appKey 定制白名单。
     * <ul>
     *   <li>{@code null}：忽略</li>
     *   <li>空集合：删除定制，回落全局默认</li>
     *   <li>非空：覆盖；元素兼容 {@link Byte}/{@link Number}</li>
     * </ul>
     */
    public static void replaceAppKeyWhitelist(String appKey, Collection<?> deviceTypes) {
        if (StringUtils.isBlank(appKey)) {
            log.error("appKey 为空，忽略设备类型更新");
            return;
        }
        if (deviceTypes == null) {
            log.error("appKey={} 设备类型列表为 null，忽略更新", appKey);
            return;
        }
        if (deviceTypes.isEmpty()) {
            APP_KEY_CACHE.delete(appKey);
            log.info("已清除 appKey={} 定制设备类型，回落全局默认", appKey);
            return;
        }
        Map<Byte, Byte> normalized = toIdentityMap(deviceTypes);
        if (normalized.isEmpty()) {
            log.error("appKey={} 设备类型列表无有效 Byte/Number 元素，忽略更新", appKey);
            return;
        }
        APP_KEY_CACHE.put(appKey, normalized);
    }

    /**
     * appKey 是否支持该设备类型（定制优先，否则全局）。
     */
    public static boolean supports(String appKey, byte deviceTypeValue) {
        return resolve(appKey, deviceTypeValue) != null;
    }

    /**
     * identity 在 appKey 下是否支持该设备类型（含客户端级白名单交集）。
     */
    public static boolean supports(String appKey, String identity, byte deviceTypeValue) {
        return list(appKey, identity).contains(deviceTypeValue);
    }

    /**
     * 解析合法设备类型；不支持返回 {@code null}（软失败）。
     */
    public static Byte resolve(String appKey, byte deviceTypeValue) {
        Map<Byte, Byte> appKeyMap = StringUtils.isBlank(appKey) ? null : APP_KEY_CACHE.get(appKey);
        if (MapUtils.isNotEmpty(appKeyMap)) {
            Byte deviceType = appKeyMap.get(deviceTypeValue);
            if (deviceType == null) {
                log.warn("appKey={} 暂未支持设备类型：{}", appKey, deviceTypeValue);
            }
            return deviceType;
        }
        Byte deviceType = DEFAULT_CACHE.get(deviceTypeValue);
        if (deviceType == null) {
            log.warn("非法设备类型：{}", deviceTypeValue);
        }
        return deviceType;
    }

    /**
     * appKey 支持的设备类型；无定制则返回全局默认集合。
     */
    public static Collection<Byte> list(String appKey) {
        if (StringUtils.isNotBlank(appKey)) {
            Map<Byte, Byte> appKeyMap = APP_KEY_CACHE.get(appKey);
            if (MapUtils.isNotEmpty(appKeyMap)) {
                return appKeyMap.values();
            }
        }
        return DEFAULT_CACHE.asMap().values();
    }

    /**
     * identity 支持的设备类型 = 客户端定制 ∩ appKey/全局；定制脏数据静默丢弃。
     * <p>交集为空时回落 appKey/全局，避免脏配置导致完全不可登录。</p>
     */
    public static Collection<Byte> list(String appKey, String identity) {
        Collection<Byte> appKeySupported = list(appKey);
        if (StringUtils.isBlank(identity)) {
            return appKeySupported;
        }
        ClientInfo clientInfo = MessageContext.localClientInfo(appKey, identity);
        if (clientInfo == null || CollectionUtils.isEmpty(clientInfo.getSupportDeviceTypes())) {
            return appKeySupported;
        }
        Set<Byte> allowed = new HashSet<>(appKeySupported);
        Collection<Byte> intersected = Lists.newArrayList();
        for (Byte supportDeviceType : clientInfo.getSupportDeviceTypes()) {
            if (supportDeviceType != null && allowed.contains(supportDeviceType)) {
                intersected.add(supportDeviceType);
            } else if (supportDeviceType != null) {
                log.warn("identity={} 定制设备类型 {} 不在 appKey={} 白名单内，已忽略",
                        identity, supportDeviceType, appKey);
            }
        }
        return CollectionUtils.isEmpty(intersected) ? appKeySupported : intersected;
    }

    /**
     * Redis/JSON 反序列化集合 → 恒等 Map（key=value）。
     */
    public static Map<Byte, Byte> toIdentityMap(Collection<?> deviceTypes) {
        Map<Byte, Byte> map = new HashMap<>();
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return map;
        }
        for (Object item : deviceTypes) {
            Byte value = toByte(item);
            if (value != null) {
                map.put(value, value);
            }
        }
        return map;
    }

    /**
     * Redis Set / JSON 元素可能是 Byte 或 Integer/Long 等 Number。
     */
    public static Byte toByte(Object raw) {
        if (raw instanceof Byte b) {
            return b;
        }
        if (raw instanceof Number number) {
            return number.byteValue();
        }
        return null;
    }
}
