package com.ouyunc.cache.distributed.redis;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.ouyunc.cache.distributed.AbstractDistributedCache;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * @author fzx
 * @description 分布式缓存redis 实现
 */
public class RedisDistributedCache<K, V> extends AbstractDistributedCache<K,V> {
    /**
     * redis 缓存模板
     */
    private final RedisTemplate<K, V> redisTemplate;

    /**
     * redis 缓存模板
     */
    private final StringRedisTemplate stringRedisTemplate;


    public RedisDistributedCache(RedisTemplate<K, V> redisTemplate, StringRedisTemplate stringRedisTemplate) {
        this.redisTemplate = redisTemplate;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @SuppressWarnings("unchecked")
    @Override
    public RedisTemplate<K, V> instance() {
        return redisTemplate;
    }

    @Override
    public void put(K key, V value) {
        redisTemplate.opsForValue().set(key, value);
    }

    @Override
    public void putAll(Map<? extends K, ? extends V> keyValueMap) {
        redisTemplate.opsForValue().multiSet(keyValueMap);
    }

    @Override
    public Boolean putIfAbsent(K key, V value) {
        return redisTemplate.opsForValue().setIfAbsent(key, value);
    }

    @Override
    public V get(K key) {
        return redisTemplate.opsForValue().get(key);
    }

    @Override
    public List<V> getAll(Set<K> keys) {
        List<V> vs = redisTemplate.opsForValue().multiGet(keys);
        if (CollectionUtils.isEmpty(vs)) {
            return Lists.newArrayList();
        }
        return vs.stream().filter(Objects::nonNull).collect(Collectors.toList());
    }

    /**
     * @Author fzx
     * @Description 获取多个key对应的值
     */
    @Override
    public Map<K, V> getAllMap(Set<K> keys) {
        List<V> values = redisTemplate.opsForValue().multiGet(keys);
        if (CollectionUtils.isEmpty(values) || keys.isEmpty()) {
            return new HashMap<>();
        }
        Map<K, V> resultMap = Maps.newHashMap();
        Iterator<K> keyIterator = keys.iterator();
        Iterator<V> valueIterator = values.iterator();

        while (keyIterator.hasNext() && valueIterator.hasNext()) {
            K key = keyIterator.next();
            V value = valueIterator.next();
            if (value != null) {
                resultMap.put(key, value);
            }
        }
        return resultMap;
    }

    @Override
    public void delete(K key) {
        redisTemplate.delete(key);
    }

    @Override
    public void deleteAll(Set<K> keys) {
        redisTemplate.delete(keys);
    }

    @Override
    public void put(K key, V value, long timeout, TimeUnit unit) {
        if (timeout <= 0) {
            redisTemplate.opsForValue().set(key, value);
            return;
        }
        redisTemplate.opsForValue().set(key, value, timeout, unit);
    }

    @Override
    public void expire(K key, long timeout, TimeUnit unit) {
        redisTemplate.expire(key, timeout, unit);
    }

    @Override
    public void batchExpire(List<K> keys, long timeout, TimeUnit unit) {
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <KK, V> Object execute(RedisOperations<KK, V> operations) throws DataAccessException {
                for (K key : keys) {
                    operations.expire((KK) key, timeout, unit);
                }
                return null;
            }
        });
    }

    @Override
    public void putHash(K key, Object hashKey, V value) {
        redisTemplate.opsForHash().put(key, hashKey, value);
    }

    @Override
    public Boolean putHashIfAbsent(K key, Object hashKey, V value) {
        return redisTemplate.opsForHash().putIfAbsent(key, hashKey, value);
    }

    @Override
    public void putHashAll(K key, Map<Object, ? extends V> hashKeyValue) {
        redisTemplate.opsForHash().putAll(key, hashKeyValue);
    }

    @SuppressWarnings("unchecked")
    @Override
    public V getHash(K key, Object hashKey) {
        return (V) redisTemplate.opsForHash().get(key, hashKey);
    }

    @SuppressWarnings("unchecked")
    @Override
    public Map<K, V> getHashAll(K key) {
        return (Map<K, V>) redisTemplate.opsForHash().entries(key);
    }

    @Override
    public Boolean deleteHashAll(K key) {
        return redisTemplate.delete(key);
    }

    @Override
    public Long deleteHash(K key, Object... hashKeys) {
        return redisTemplate.opsForHash().delete(key, hashKeys);
    }

    @Override
    public Boolean addZset(K key, V value, double score) {
        return stringRedisTemplate.opsForZSet().add((String) key, (String) value, score);
    }

    @Override
    public Long sizeZset(K key) {
        return stringRedisTemplate.opsForZSet().zCard((String) key);
    }

    @Override
    public Double scoreZset(K key, Object object) {
        return stringRedisTemplate.opsForZSet().score((String) key, object);
    }
}
