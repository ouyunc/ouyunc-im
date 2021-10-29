package com.ouyu.cache.l1.local;

import com.ouyu.cache.l1.L1Cache;

import java.util.concurrent.ConcurrentMap;

/**
 * @Author fangzhenxun
 * @Description: 抽象本地L1 cache
 * @Version V1.0
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

}
