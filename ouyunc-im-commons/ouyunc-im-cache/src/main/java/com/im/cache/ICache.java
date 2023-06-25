package com.im.cache;

/**
 * @Author fangzhenxun
 * @Description: 抽象总父接口
 **/
public interface ICache<K,V> {
    /**
     * 存放数据
     */
    void put(K key, V value);



    /**
     * 获取数据，如果没有则返回null
     */
    V get(K key);


    /**
     * 删除数据
     */
    void delete(K key);



}
