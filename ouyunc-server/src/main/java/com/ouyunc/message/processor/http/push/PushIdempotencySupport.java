package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 推送幂等（appKey + messageId）：校验通过后再 {@code SETNX}，值即 packetId，表示已成功受理。
 * <p>状态只有「无 / 已成功」，无 PENDING 阶段。</p>
 */
public final class PushIdempotencySupport {

    private static final DefaultRedisScript<Long> RELEASE_IF_MATCH_SCRIPT = new DefaultRedisScript<>();

    static {
        RELEASE_IF_MATCH_SCRIPT.setResultType(Long.class);
        RELEASE_IF_MATCH_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
    }

    private PushIdempotencySupport() {
    }

    /** 已受理时的 packetId；无则 null。 */
    @SuppressWarnings("unchecked")
    public static String getPacketId(String appKey, String messageId) {
        if (StringUtils.isAnyBlank(appKey, messageId)) {
            return null;
        }
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        Object value = redisTemplate.opsForValue().get(key);
        return value == null ? null : String.valueOf(value);
    }

    /** 校验通过后占位；成功返回 true。 */
    @SuppressWarnings("unchecked")
    public static boolean tryClaim(String appKey, String messageId, String packetId) {
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        long ttl = idempotentTtlSeconds();
        Boolean ok = redisTemplate.opsForValue().setIfAbsent(key, packetId, ttl, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(ok);
    }

    /** 仅释放本请求占位（提交后台失败时回滚，便于重试）。 */
    @SuppressWarnings("unchecked")
    public static boolean releaseIfOwned(String appKey, String messageId, String packetId) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId)) {
            return false;
        }
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        Long deleted = redisTemplate.execute(RELEASE_IF_MATCH_SCRIPT, Collections.singletonList(key), packetId);
        return deleted != null && deleted > 0;
    }

    private static long idempotentTtlSeconds() {
        long ttl = MessageServerContext.serverProperties().getHttpPushIdempotentTtlSeconds();
        return ttl > 0 ? ttl : 86400L;
    }
}
