package com.ouyunc.cache.distributed.redis;

import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.AbstractDistributedCache;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @author fzx
 * @description 分布式缓存redis 实现
 */
public class RedisDistributedCache<K, V> extends AbstractDistributedCache<K,V> {
    /**
     * redis 缓存模板
     */
    private RedisTemplate<K, V> redisTemplate = CacheFactory.REDIS.instance();

    public RedisDistributedCache() {
    }

    public RedisDistributedCache(RedisTemplate<K, V> redisTemplate) {
        this.redisTemplate = redisTemplate;
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
        return redisTemplate.opsForValue().multiGet(keys);
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
        return redisTemplate.opsForZSet().add(key, value, score);
    }

    @Override
    public Long sizeZset(K key) {
        return redisTemplate.opsForZSet().zCard(key);
    }
}
