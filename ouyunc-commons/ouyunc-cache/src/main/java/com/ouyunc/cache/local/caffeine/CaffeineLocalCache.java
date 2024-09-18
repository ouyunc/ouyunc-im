package com.ouyunc.cache.local.caffeine;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.cache.local.AbstractLocalCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.concurrent.ConcurrentMap;

/**
 * @Author fzx
 * @Description: 线程安全
 **/
public class CaffeineLocalCache<K , V > extends AbstractLocalCache<K, V> {
    private static final Logger log = LoggerFactory.getLogger(CaffeineLocalCache.class);

    /**
     * 缓存名称
     */
    private final String cacheName;

    /**
     * 自动缓存
     */
    private final LoadingCache<K, V> loadingCache;

    public String getCacheName() {
        return cacheName;
    }

    public CaffeineLocalCache(String cacheName, LoadingCache<K, V> loadingCache) {
        this.cacheName = cacheName;
        this.loadingCache = loadingCache;
    }

    /**
     * @Author fzx
     * @Description 如果有值就覆盖
     */
    @Override
    public void put(K key, V value) {
        loadingCache.put(key, value);
    }


    /**
     * @Author fzx
     * @Description 添加全部缓存
     */
    @Override
    public void putAll(Map<? extends K, ? extends V> keyValueMap) {
        loadingCache.putAll(keyValueMap);
    }


    /**
     * @Author fzx
     * @Description 如果有key对应的值就返回，不做操作，如果没有就添加
     */
    @Override
    public V putIfAbsent(K key, V value) {
        V v = loadingCache.get(key, k -> value);
        if (Objects.equals(value, v)) {
            return null;
        }
        return v;
    }




    /**
     * @Author fzx
     * @Description 如果key 对应的值，没有返回null
     */
    @Override
    public V get(K key) {
        return loadingCache.get(key);
    }

    /**
     * @Author fzx
     * @Description 获取多个key对应的值
     */
    @Override
    public Collection<V> getAll(Set<K> keys) {
        Map<K, V> kvMap = loadingCache.getAll(keys);
        if (kvMap == null) {
            return null;
        }
        return kvMap.values();
    }

    /**
     * @Author fzx
     * @Description 删除key 对应的值
     */
    @Override
    public void delete(K key) {
        loadingCache.invalidate(key);
    }

    /**
     * @Author fzx
     * @Description 删除多个key对应的值
     */
    @Override
    public void deleteAll(Set<K> keys) {
        loadingCache.invalidateAll(keys);
    }

    /**
     * @Author fzx
     * @Description 将内存转成Map
     */
    @Override
    public ConcurrentMap<K, V> asMap() {
        return loadingCache.asMap();
    }

    /**
     * @Author fzx
     * @Description 获取内存大小
     */
    @Override
    public long sizeMap() {
        return loadingCache.estimatedSize();
    }
}
