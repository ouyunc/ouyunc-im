package com.im.cache.l1.distributed;

import com.im.cache.l1.L1Cache;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author fangzhenxun
 * @Description: 抽象分布式 L1 cache
 * @Version V3.0
 **/
public abstract class AbstractDistributedL1Cache<K, V> implements L1Cache<K, V> {

    /**
     * 存放数据,有过期时间
     */
    public abstract void put(K key, V value, long timeout, TimeUnit unit);


    /**
     * 存放数据,如果该key 存在则返回旧值，如果不存在则设置新值，且返回null
     */
    public abstract Boolean putIfAbsent(K key, V value);

    /**
     * 存放hash值
     */
    public abstract void putHash(K key, Object hashKey, V value);

    /**
     * 存放hash值Boolean
     */
    public abstract Boolean putHashIfAbsent(K key, Object hashKey, V value);

    /**
     * 存放设置 key 指定的哈希集中指定字段的值。该命令将重写所有在哈希集中存在的字段。如果 key 指定的哈希集不存在，会创建一个新的哈希集并与 key 关联
     */
    public abstract void putHashAll(K key, Map<? extends K, ? extends V> value);

    /**
     * 获取hash值
     */
    public abstract V getHash(K key, Object  hashKey);

    /**
     * 获取所有hansh值
     */
    public abstract Map<K, V> getHashAll(K key);

    /**
     * 删除hash中的键
     */
    public abstract Long deleteHashAll(K key);

    /**
     * 删除hash中的某几个值
     */
    public abstract Long deleteHash(K key, Object... hashKeys);


    /**
     * 添加zset
     */
    public abstract Boolean addZset(K key, Object value, double score);


    /**
     * 获取zset 大小
     */
    public abstract Long sizeZset(K key);



    /**
     *  随机获取指定key，指定个数
     */
    public abstract List<Object> getRandomMembersZset(K key, long count);

    /**
     * 随机获取单个值
     */
    public abstract Object getRandomMemberZset(K key);


    public abstract Set<Object> rangeZset(K key, long start, long end);

    public abstract Set<Object> reverseRangeZset(K key, long start, long end);

    /**
     * zset  根据key和value移除指定元素，未查询到对应的key和value，返回0，否则返回1
     */
    public abstract Long removeZset(K key, Object value);

    /**
     * zset  获取对应key和value的score
     */
    public abstract Double score(K key, Object value);

    /**
     * zset  指定范围内元素排序
     */
    public abstract Set<Object> rangeByScore(K key, double min, double max);
    /**
     * zset  指定范围内元素排序
     */
    public abstract Set<Object> reverseRangeByScore(K key, double min, double max);

    /**
     * zset  指定范围内元素排序, 从哪里开始要多少
     */
    public abstract Set<Object>  rangeByScore(K key, double min, double max, long offset, long count);
    /**
     * zset  指定范围内元素排序, 从哪里开始要多少
     */
    public abstract Set<Object>  reverseRangeByScore(K key, double min, double max, long offset, long count);

    /**
     * zset  元素在集合内对应的排名
     */
    public abstract  Long rank(K key, Object obj);

    /**
     * zset  倒序排列元素在集合内对应的排名
     */
    public abstract  Long reverseRank(K key, Object obj);



}
