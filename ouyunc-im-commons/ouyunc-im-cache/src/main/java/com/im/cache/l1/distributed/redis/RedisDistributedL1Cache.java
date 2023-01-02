package com.im.cache.l1.distributed.redis;

import com.im.cache.l1.distributed.AbstractDistributedL1Cache;
import com.im.cache.l1.distributed.redis.lettuce.RedisFactory;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

public class RedisDistributedL1Cache <K , V > extends AbstractDistributedL1Cache<K, V> {

    /**
     * redis 缓存模板
     */
    private RedisTemplate redisTemplate = RedisFactory.redisTemplate(0);

    public RedisDistributedL1Cache() {
    }

    public RedisDistributedL1Cache(RedisTemplate<K, V> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    /**
     * 存入redis
     * @param key
     * @param value
     */
    @Override
    public void put(K key, V value) {
        redisTemplate.opsForValue().set(key, value);
    }

    /**
     * 根据key 获取对应值
     * @param key
     * @return
     */
    @Override
    public V get(K key) {
        return (V) redisTemplate.opsForValue().get(key);
    }

    /**
     * 根据key 删除对应数据
     * @param key
     */
    @Override
    public void delete(K key) {
        redisTemplate.delete(key);
    }

    /**
     * 存储key-value并设置过期时间
     * @param key
     * @param value
     * @param timeout
     * @param unit
     */
    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        redisTemplate.opsForValue().set(key,value, timeout, unit);
    }

    /**
     *1.如果键不存在则新增,存在则不改变已经有的值。
     *2.存在返回 false，不存在返回 true。
     * @param key
     * @param value
     * @return
     */
    @Override
    public Boolean putIfAbsent(K key, V value) {
        return redisTemplate.opsForValue().setIfAbsent(key,value);
    }

    /**
     *新增hashMap值
     * @param key
     * @param hashKey
     * @param value
     */
    @Override
    public void putHash(K key, Object hashKey, V value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    /**
     * 如果变量值存在，在变量中可以添加不存在的的键值对，如果变量不存在，则新增一个变量，同时将键值对添加到该变量。
     * @param key
     * @param hashKey
     * @param value
     * @return
     */
    @Override
    public Boolean putHashIfAbsent(K key, Object hashKey, V value) {
        return redisTemplate.opsForHash().putIfAbsent(key, hashKey, value);
    }

    /**
     *  以map集合的形式添加键值对。
     * @param key
     * @param value
     */
    @Override
    public void putHashAll(K key, Map<Object, ? extends V> value) {
        redisTemplate.opsForHash().putAll(key, value);
    }

    /**
     * 获取hash 值
     * @param key
     * @param hashKey
     * @return
     */
    @Override
    public V getHash(K key, Object hashKey) {
        return (V) redisTemplate.opsForHash().get(key, hashKey);
    }

    /**
     * 获取map
     * @param key
     * @return
     */
    @Override
    public Map<K, V> getHashAll(K key) {
        return (Map<K, V>) redisTemplate.opsForHash().entries(key);
    }

    /**
     * 删除所有
     * @param key
     * @return
     */
    @Override
    public boolean deleteHashAll(K key) {
        return redisTemplate.delete(key);
    }

    /**
     * 删除hash 中的某个值
     * @param key
     * @param hashKeys
     * @return
     */
    @Override
    public Long deleteHash(K key, Object... hashKeys) {
       return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    /**
     * 添加数据到zset
     */
    @Override
    public Boolean addZset(K key, Object value, double score) {
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    @Override
    public Long sizeZset(K key) {
        return redisTemplate.opsForZSet().size(key);
    }

    @Override
    public<T> List<T> getRandomMembersZset(K key, long count) {
        return redisTemplate.opsForZSet().randomMembers(key, count);
    }

    @Override
    public<T> T getRandomMemberZset(K key) {
        return (T) redisTemplate.opsForZSet().randomMember(key);
    }


    @Override
    public<T> Set<T> rangeZset(K key, long start, long end) {
        return redisTemplate.opsForZSet().range(key, start, end);
    }

    @Override
    public<T> Set<T> reverseRangeZset(K key, long start, long end) {
        return redisTemplate.opsForZSet().reverseRange(key, start, end);
    }

    @Override
    public Long removeZset(K key, Object value) {
        return redisTemplate.opsForZSet().remove(key, value);
    }

    @Override
    public Double score(K key, Object value) {
        return null;
    }

    @Override
    public<T> Set<T> rangeByScore(K key, double min, double max) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max);
    }

    @Override
    public<T> Set<T> reverseRangeByScore(K key, double min, double max) {
        return redisTemplate.opsForZSet().reverseRangeByScore(key, min, max);
    }

    @Override
    public<T> Set<T> rangeByScore(K key, double min, double max, long offset, long count) {
        return redisTemplate.opsForZSet().rangeByScore(key, min, max, offset, count);
    }

    @Override
    public<T> Set<T> reverseRangeByScore(K key, double min, double max, long offset, long count) {
        return redisTemplate.opsForZSet().reverseRangeByScore(key, min, max, offset, count);
    }

    @Override
    public Long rank(K key, Object obj) {
        return redisTemplate.opsForZSet().rank(key, obj);
    }

    @Override
    public Long reverseRank(K key, Object obj) {
        return  redisTemplate.opsForZSet().reverseRank(key, obj);
    }
}
