package com.ouyu.cache.l1.local.map;

import com.ouyu.cache.l1.local.AbstractLocalL1Cache;
import com.ouyu.cache.l1.local.caffeine.CaffeineLocalL1Cache;
import org.checkerframework.checker.units.qual.C;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 本地map 缓存，线程安全
 * @Version V1.0
 **/
public class MapLocalL1Cache<K,V> extends AbstractLocalL1Cache<K,V> {
    private static Logger log = LoggerFactory.getLogger(MapLocalL1Cache.class);


    private String cacheName;

    private ConcurrentMap<K, V> concurrentMap;

    public String getCacheName() {
        return cacheName;
    }

    public MapLocalL1Cache(String cacheName, ConcurrentMap<K, V> concurrentMap) {
        this.cacheName = cacheName;
        this.concurrentMap = concurrentMap;
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return concurrentMap;
    }

    @Override
    public long size() {
        return concurrentMap.size();
    }

    @Override
    public void put(K key, V value) {
        concurrentMap.put(key,value);
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        log.warn("暂不支持该方法!");
        throw new RuntimeException("暂不支持该方法");
    }

    @Override
    public Boolean putIfAbsent(K key, V value) {
        V v = concurrentMap.putIfAbsent(key, value);
        if (v != null) {
            return true;
        }
        return false;
    }


    @Override
    public V get(K key) {
        return concurrentMap.get(key);
    }

    @Override
    public void delete(K key) {
        concurrentMap.remove(key);
    }

    @Override
    public Boolean putHash(K key, K hashKey, V value) {
        return null;
    }

    @Override
    public Boolean putHashIfAbsent(K key, K hashKey, V value) {
        return null;
    }

    @Override
    public void putHashAll(K key, Map<? extends K, ? extends V> value) {

    }

    @Override
    public V getHash(K key, K hashKey) {
        return null;
    }

    @Override
    public Map<K, V> getHashAll(K key) {
        return null;
    }

    @Override
    public Long deleteHashAll(K key) {
        return null;
    }

    @Override
    public Long deleteHash(K key, K... hashKeys) {
        return null;
    }
}
