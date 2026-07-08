package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.springframework.data.redis.core.RedisTemplate;

import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送幂等：基于 appKey + messageId。
 */
public final class PushIdempotencySupport {

    private PushIdempotencySupport() {
    }

    /**
     * @return true 表示首次受理；false 表示重复
     */
    @SuppressWarnings("unchecked")
    public static boolean tryClaim(String appKey, String messageId, String packetId) {
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        long ttl = MessageServerContext.serverProperties().getHttpPushIdempotentTtlSeconds();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, packetId, ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    @SuppressWarnings("unchecked")
    public static String findClaimedPacketId(String appKey, String messageId) {
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        Object value = redisTemplate.opsForValue().get(key);
        return value != null ? String.valueOf(value) : null;
    }

    /**
     * 校验或处理失败时释放幂等占位，允许调用方修正后重试同一 messageId。
     */
    @SuppressWarnings("unchecked")
    public static void releaseClaim(String appKey, String messageId) {
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        redisTemplate.delete(key);
    }
}
