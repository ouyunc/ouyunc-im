package com.ouyunc.cache.distributed.redis;

import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Cluster 安全的批量 STRING 操作：每个 key 一条 GET/SET/DEL，走管道按槽路由。
 * {@code MGET}/{@code MSET}/多 key {@code DEL} 在不同槽会 CROSSSLOT。
 */
public final class RedisPipelineSupport {

    private RedisPipelineSupport() {
    }

    public static List<String> getStrings(StringRedisTemplate template, List<String> keys) {
        if (template == null || keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        RedisSerializer<String> keySerializer = template.getStringSerializer();
        List<Object> raw = template.executePipelined((RedisCallback<Object>) connection -> {
            getEach(connection, keySerializer, keys);
            return null;
        });
        List<String> values = new ArrayList<>(keys.size());
        for (int i = 0; i < keys.size(); i++) {
            Object item = raw == null || i >= raw.size() ? null : raw.get(i);
            values.add(item == null ? null : item.toString());
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public static <V> List<V> getValues(RedisTemplate<?, ?> template, Collection<String> keys) {
        if (template == null || keys == null || keys.isEmpty()) {
            return Collections.emptyList();
        }
        List<String> ordered = keys instanceof List<String> list ? list : new ArrayList<>(keys);
        RedisTemplate<String, V> typed = (RedisTemplate<String, V>) template;
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) typed.getKeySerializer();
        List<Object> raw = typed.executePipelined((RedisCallback<Object>) connection -> {
            getEach(connection, keySerializer, ordered);
            return null;
        });
        List<V> values = new ArrayList<>(ordered.size());
        for (int i = 0; i < ordered.size(); i++) {
            Object item = raw == null || i >= raw.size() ? null : raw.get(i);
            values.add((V) item);
        }
        return values;
    }

    @SuppressWarnings("unchecked")
    public static <V> void setValues(RedisTemplate<?, ?> template, Map<String, ? extends V> keyValues) {
        if (template == null || keyValues == null || keyValues.isEmpty()) {
            return;
        }
        RedisTemplate<String, V> typed = (RedisTemplate<String, V>) template;
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) typed.getKeySerializer();
        RedisSerializer<V> valueSerializer = (RedisSerializer<V>) typed.getValueSerializer();
        typed.executePipelined((RedisCallback<Object>) connection -> {
            for (Map.Entry<String, ? extends V> entry : keyValues.entrySet()) {
                byte[] rawKey = keySerializer.serialize(entry.getKey());
                byte[] rawVal = valueSerializer.serialize(entry.getValue());
                if (rawKey == null || rawVal == null) {
                    throw new IllegalStateException("Redis 序列化失败");
                }
                connection.stringCommands().set(rawKey, rawVal);
            }
            return null;
        });
    }

    @SuppressWarnings("unchecked")
    public static void deleteKeys(RedisTemplate<?, ?> template, Collection<String> keys) {
        if (template == null || keys == null || keys.isEmpty()) {
            return;
        }
        RedisSerializer<String> keySerializer = (RedisSerializer<String>) template.getKeySerializer();
        template.executePipelined((RedisCallback<Object>) connection -> {
            for (String key : keys) {
                byte[] rawKey = keySerializer.serialize(key);
                if (rawKey == null) {
                    throw new IllegalStateException("Redis key 序列化失败");
                }
                connection.keyCommands().del(rawKey);
            }
            return null;
        });
    }

    /**
     * SessionCallback 管道内逐 key SET，禁止 {@code MSET}。
     */
    @SuppressWarnings("unchecked")
    public static <K, V> void setEach(RedisOperations<K, V> operations, Map<String, ?> keyValues) {
        if (operations == null || keyValues == null || keyValues.isEmpty()) {
            return;
        }
        for (Map.Entry<String, ?> entry : keyValues.entrySet()) {
            operations.opsForValue().set((K) entry.getKey(), (V) entry.getValue());
        }
    }

    /**
     * SessionCallback 管道内逐 key DEL，禁止多 key {@code DEL}。
     */
    @SuppressWarnings("unchecked")
    public static <K, V> void deleteEach(RedisOperations<K, V> operations, Collection<String> keys) {
        if (operations == null || keys == null || keys.isEmpty()) {
            return;
        }
        for (String key : keys) {
            operations.delete((K) key);
        }
    }

    private static void getEach(RedisConnection connection, RedisSerializer<String> keySerializer, List<String> keys) {
        for (String key : keys) {
            byte[] rawKey = keySerializer.serialize(key);
            if (rawKey == null) {
                throw new IllegalStateException("Redis key 序列化失败");
            }
            connection.stringCommands().get(rawKey);
        }
    }
}
