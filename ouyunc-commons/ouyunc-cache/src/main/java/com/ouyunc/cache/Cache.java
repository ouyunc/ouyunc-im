package com.ouyunc.cache;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: 缓存接口, 这里没有使用jetCache框架，选择自己封装了下
 **/
public interface Cache<K,V> {

    /**
     * 存放数据
     */
    void put(K key, V value);


    /**
     * 批量存放数据，key 重复会覆盖
     */
    void putAll(Map<? extends K, ? extends V> keyValueMap);


    /**
     * 如果不存在（新的entry），那么会向map中添加该键值对，并返回null。
     * 如果已经存在，那么不会覆盖已有的值，直接返回已经存在的值
     */
    Object putIfAbsent(K key, V value);

    /**
     * 获取数据，如果没有则返回null
     */
    V get(K key);

    /**
     * 获取所有数据
     */
    Collection<V> getAll(Set<K> keys);

    /**
     * 删除数据
     */
    void delete(K key);

    /**
     * 删除所有数据
     */
    void deleteAll(Set<K> keys);


    /**
     * 存放数据，过期时间
     */
    default void put(K key, V value, long timeout, TimeUnit unit){
        throw new UnsupportedOperationException();
    }

    /**
     * 设置过期时间
     */
    default void expire(K key, long timeout, TimeUnit unit){
        throw new UnsupportedOperationException();
    }
    /**
     * 设置过期时间
     */
    default void batchExpire(List<K> keys, long timeout, TimeUnit unit){
        throw new UnsupportedOperationException();
    }


    /**
     * 将本地缓存转成Map
     */
    default ConcurrentMap<K,V> asMap() {
        throw new UnsupportedOperationException();
    }

    /**
     * 缓存map数据大小
     */
    default long sizeMap() {
        throw new UnsupportedOperationException();
    }


    /**
     * 存放hash值
     */
    default void putHash(K key, Object hashKey, V value){
        throw new UnsupportedOperationException();
    }

    /**
     * 存放hash值Boolean
     */
    default Boolean putHashIfAbsent(K key, Object hashKey, V value){
        throw new UnsupportedOperationException();
    }

    /**
     * 存放设置 key 指定的哈希集中指定字段的值。该命令将重写所有在哈希集中存在的字段。如果 key 指定的哈希集不存在，会创建一个新的哈希集并与 key 关联
     */
    default void putHashAll(K key, Map<Object, ? extends V> hashKeyValue){
        throw new UnsupportedOperationException();
    }

    /**
     * 获取hash值
     */
    default V getHash(K key, Object  hashKey){
        throw new UnsupportedOperationException();
    }

    /**
     * 获取所有hansh值
     */
    default Map<K, V> getHashAll(K key){
        throw new UnsupportedOperationException();
    }

    /**
     * 删除hash中的键
     */
    default Boolean deleteHashAll(K key){
        throw new UnsupportedOperationException();
    }

    /**
     * 删除hash中的某几个值
     */
    default Long deleteHash(K key, Object... hashKeys){
        throw new UnsupportedOperationException();
    }



    /**
     * 添加zset
     */
    default Boolean addZset(K key, V value, double score) {
        throw new UnsupportedOperationException();
    }


    /**
     * 获取zset 大小
     */
    default Long sizeZset(K key){
        throw new UnsupportedOperationException();
    }

}
