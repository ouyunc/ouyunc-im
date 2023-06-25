package com.im.cache.l1.local;

import com.im.cache.l1.L1Cache;

import java.util.concurrent.ConcurrentMap;

/**
 * @Author fangzhenxun
 * @Description: 抽象本地L1 cache
 **/
public abstract class AbstractLocalL1Cache<K, V> implements L1Cache<K, V> {

    /**
     * 将本地缓存转成Map
     */
    public abstract ConcurrentMap<K,V> asMap();

    /**
     * 缓存数据大小
     */
    public abstract long size();

    public abstract V putIfAbsent(K key, V value);
}
