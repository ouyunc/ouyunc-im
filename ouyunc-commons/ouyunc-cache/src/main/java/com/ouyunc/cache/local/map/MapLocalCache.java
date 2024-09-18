package com.ouyunc.cache.local.map;

import com.ouyunc.cache.local.AbstractLocalCache;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * @Author fzx
 * @Description: 本地map 缓存，线程安全
 **/
public class MapLocalCache<K,V> extends AbstractLocalCache<K,V> {
    private static final Logger log = LoggerFactory.getLogger(MapLocalCache.class);


    private final String cacheName;

    private final ConcurrentMap<K, V> concurrentMap;

    public String getCacheName() {
        return cacheName;
    }

    public MapLocalCache(String cacheName, ConcurrentMap<K, V> concurrentMap) {
        this.cacheName = cacheName;
        this.concurrentMap = concurrentMap;
    }

    @Override
    public ConcurrentMap<K, V> asMap() {
        return concurrentMap;
    }

    @Override
    public void put(K key, V value) {
        concurrentMap.put(key,value);
    }


    @Override
    public void putAll(Map<? extends K, ? extends V> keyValueMap) {
        concurrentMap.putAll(keyValueMap);
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
    public List<V> getAll(Set<K> keys) {
        return keys.parallelStream().map(concurrentMap::get).collect(Collectors.toList());
    }

    @Override
    public void delete(K key) {
        concurrentMap.remove(key);
    }

    /****
     * @author fzx
     * @description 删除多个keys
     */
    @Override
    public void deleteAll(Set<K> keys) {
        concurrentMap.keySet().removeAll(keys);
    }


    /**
     * @Author fzx
     * @Description 获取内存大小
     */
    @Override
    public long sizeMap() {
        return concurrentMap.size();
    }
}
