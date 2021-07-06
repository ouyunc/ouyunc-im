package com.ouyu.cache.l1.distributed.redis.lettuce.codec;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.lettuce.core.codec.RedisCodec;

import java.io.IOException;
import java.nio.ByteBuffer;

/**
 * @Author fangzhenxun
 * @Description: redis的key与value的编解码
 * @Version V1.0
 **/
public class RedisKeyValueCodec<K, V> implements RedisCodec<K,V> {

    private ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public K decodeKey(ByteBuffer byteBuffer) {
        byte[] b = new byte[byteBuffer.remaining()];
        byteBuffer.get(b, 0, b.length);
        try {
            return (K) objectMapper.readValue(b, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public V decodeValue(ByteBuffer byteBuffer) {
        byte[] b = new byte[byteBuffer.remaining()];
        byteBuffer.get(b, 0, b.length);
        try {
            return (V) objectMapper.readValue(b, Object.class);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @Override
    public ByteBuffer encodeKey(K k) {
        ByteBuffer buffer = null;
        try {
            final byte[] bytes = objectMapper.writeValueAsBytes(k);
            buffer = ByteBuffer.wrap(bytes);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return buffer;
    }

    @Override
    public ByteBuffer encodeValue(V v) {
        ByteBuffer buffer = null;
        try {
            final byte[] bytes = objectMapper.writeValueAsBytes(v);
            buffer = ByteBuffer.wrap(bytes);
        } catch (JsonProcessingException e) {
            e.printStackTrace();
        }
        return buffer;
    }
}
