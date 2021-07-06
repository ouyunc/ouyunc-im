package com.ouyu.cache;

import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 抽象总父接口
 * @Version V1.0
 **/
public interface ICache<K,V> {
    /**
     * 存放数据
     */
    void put(K key, V value);

    /**
     * 存放数据,有过期时间
     */
    void put(K key, V value, long timeout, TimeUnit unit);


    /**
     * 存放数据,如果该key 存在则返回旧值，如果不存在则设置新值，且返回null
     */
    void putIfAbsent(K key, V value);

    /**
     * 获取数据，如果没有则返回null
     */
    V get(K key);


    /**
     * 删除数据
     */
    void delete(K key);
}
