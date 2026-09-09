package com.ouyunc.cache.local.caffeine;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.cache.local.AbstractLocalCache;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;

/**
 * 本地 Caffeine 封装。{@link #get} 未命中返回 null，与接口约定一致；
 * Caffeine 3.1 CacheLoader 禁止返回 null 时捕获后按 miss 处理；3.2+ 允许 null 则直接返回。
 */
public class CaffeineLocalCache<K, V> extends AbstractLocalCache<K, V> {

    private static final String INVALID_CACHE_LOAD = "InvalidCacheLoadException";

    private final String cacheName;

    private final Cache<K, V> cache;

    public String getCacheName() {
        return cacheName;
    }

    public CaffeineLocalCache(String cacheName, Cache<K, V> cache) {
        this.cacheName = cacheName;
        this.cache = cache;
    }

    /**
     * 非 LoadingCache 的手动表（如本地连接注册表）用这个，避免与 {@code build(CacheLoader)} 重载纠缠。
     */
    public static <K, V> CaffeineLocalCache<K, V> wrap(String cacheName, Cache<K, V> nativeCache) {
        return new CaffeineLocalCache<>(cacheName, nativeCache);
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T instance() {
        return (T) cache;
    }

    @Override
    public void put(K key, V value) {
        cache.put(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> keyValueMap) {
        cache.putAll(keyValueMap);
    }

    @Override
    public V putIfAbsent(K key, V value) {
        V v = cache.get(key, k -> value);
        if (Objects.equals(value, v)) {
            return null;
        }
        return v;
    }

    /**
     * 未命中返回 null。会先 {@code getIfPresent}，再对 LoadingCache 触发 load；load 返回 null 时不抛给调用方。
     */
    @Override
    public V get(K key) {
        V present = cache.getIfPresent(key);
        if (present != null) {
            return present;
        }
        if (!(cache instanceof LoadingCache<K, V> loadingCache)) {
            return null;
        }
        try {
            return loadingCache.get(key);
        } catch (RuntimeException e) {
            if (isInvalidCacheLoad(e)) {
                return null;
            }
            throw e;
        }
    }

    @Override
    public Collection<V> getAll(Set<K> keys) {
        Map<K, V> kvMap = getAllMap(keys);
        if (MapUtils.isEmpty(kvMap)) {
            return CollectionUtils.emptyCollection();
        }
        return kvMap.values();
    }

    @Override
    public Map<K, V> getAllMap(Set<K> keys) {
        if (keys == null || keys.isEmpty()) {
            return new HashMap<>();
        }
        Map<K, V> present = new HashMap<>(cache.getAllPresent(keys));
        if (present.size() == keys.size()) {
            return present;
        }
        if (!(cache instanceof LoadingCache<K, V> loadingCache)) {
            return present;
        }
        try {
            Map<K, V> loaded = loadingCache.getAll(keys);
            return MapUtils.isEmpty(loaded) ? present : new HashMap<>(loaded);
        } catch (RuntimeException e) {
            if (!isInvalidCacheLoad(e)) {
                throw e;
            }
            for (K key : keys) {
                if (present.containsKey(key)) {
                    continue;
                }
                V value = get(key);
                if (value != null) {
                    present.put(key, value);
                }
            }
            return present;
        }
    }

    @Override
    public void delete(K key) {
        cache.invalidate(key);
    }

    @Override
    public void deleteAll(Set<K> keys) {
        cache.invalidateAll(keys);
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return cache.asMap();
    }

    @Override
    public long sizeMap() {
        return cache.estimatedSize();
    }

    /**
     * Caffeine 3.1 抛 {@code InvalidCacheLoadException}；3.2 起该类已删除，按类名识别避免编不过。
     */
    private static boolean isInvalidCacheLoad(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            if (INVALID_CACHE_LOAD.equals(cursor.getClass().getSimpleName())) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }
}
