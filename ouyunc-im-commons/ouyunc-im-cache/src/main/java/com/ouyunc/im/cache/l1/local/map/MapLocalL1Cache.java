package com.ouyunc.im.cache.l1.local.map;

import com.ouyunc.im.cache.l1.local.AbstractLocalL1Cache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.ConcurrentMap;

/**
 * @Author fangzhenxun
 * @Description: 本地map 缓存，线程安全
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
    public V putIfAbsent(K key, V value) {
        return concurrentMap.putIfAbsent(key, value);
    }


    @Override
    public V get(K key) {
        return concurrentMap.get(key);
    }

    @Override
    public void delete(K key) {
        concurrentMap.remove(key);
    }

}
