package com.ouyunc.im.cache.l1.local.caffeine;

import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.im.cache.l1.local.AbstractLocalL1Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * @Author fangzhenxun
 * @Description: 线程安全
 **/
public class CaffeineLocalL1Cache<K , V > extends AbstractLocalL1Cache<K, V> {
    private static Logger log = LoggerFactory.getLogger(CaffeineLocalL1Cache.class);

    /**
     * 缓存名称
     */
    private String cacheName;

    /**
     * 自动缓存
     */
    private LoadingCache<K, V> loadingCache;

    public String getCacheName() {
        return cacheName;
    }

    public CaffeineLocalL1Cache(String cacheName, LoadingCache<K, V> loadingCache) {
        this.cacheName = cacheName;
        this.loadingCache = loadingCache;
    }

    /**
     * @Author fangzhenxun
     * @Description 如果有值就覆盖
     * @param key
     * @param value
     * @return void
     */
    @Override
    public void put(K key, V value) {
        loadingCache.put(key, value);
    }



    /**
     * @Author fangzhenxun
     * @Description 如果有key对应的值就返回，不做操作，如果没有就添加
     * @param key
     * @param value
     * @return void
     */
    @Override
    public V putIfAbsent(K key, V value) {
        return loadingCache.get(key, k -> value);
    }




    /**
     * @Author fangzhenxun
     * @Description 如果key 对应的值，没有返回null
     * @param key
     * @return void
     */
    @Override
    public V get(K key) {
        return loadingCache.get(key);
    }

    /**
     * @Author fangzhenxun
     * @Description 删除key 对应的值
     * @param key
     * @return void
     */
    @Override
    public void delete(K key) {
        loadingCache.invalidate(key);
    }

    /**
     * @Author fangzhenxun
     * @Description 将内存转成Map
     * @return void
     */
    @Override
    public ConcurrentMap<K, V> asMap() {
        return loadingCache.asMap();
    }

    @Override
    public long size() {
        return loadingCache.estimatedSize();
    }

}
