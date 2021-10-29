package com.ouyu.cache;

import java.util.Map;
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
    Boolean putIfAbsent(K key, V value);



    /**
     * 获取数据，如果没有则返回null
     */
    V get(K key);


    /**
     * 删除数据
     */
    void delete(K key);


    /**
     * 存放hash值
     */
    Boolean putHash(K key, K hashKey, V value);

    /**
     * 存放hash值Boolean
     */
    Boolean putHashIfAbsent(K key, K hashKey, V value);

    /**
     * 存放设置 key 指定的哈希集中指定字段的值。该命令将重写所有在哈希集中存在的字段。如果 key 指定的哈希集不存在，会创建一个新的哈希集并与 key 关联
     */
    void putHashAll(K key, Map<? extends K, ? extends V> value);

    /**
     * 获取hash值
     */
    V getHash(K key, K  hashKey);

    /**
     * 获取所有hansh值
     */
    Map<K, V> getHashAll(K key);

    /**
     * 删除hash中的键
     */
    Long deleteHashAll(K key);

    /**
     * 删除hash中的某几个值
     */
    Long deleteHash(K key, K... hashKeys);



}
