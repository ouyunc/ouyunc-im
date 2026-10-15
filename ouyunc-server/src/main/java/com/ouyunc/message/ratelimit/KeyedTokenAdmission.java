package com.ouyunc.message.ratelimit;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.time.Duration;

/**
 * 按键复用令牌桶。空闲键会过期，避免来源地址或 appKey 无限增长。
 * 活跃键在每次访问时续期，攻击流量不会因为过期把突发容量重新装满。
 */
public final class KeyedTokenAdmission {

    private final Cache<String, LocalTokenBucket> buckets;
    private final double ratePerSecond;
    private final double burst;

    public KeyedTokenAdmission(long maximumSize, Duration expireAfterAccess, double ratePerSecond, double burst) {
        if (maximumSize <= 0L || expireAfterAccess == null || expireAfterAccess.isZero() || expireAfterAccess.isNegative()) {
            throw new IllegalArgumentException("admission cache bounds must be positive");
        }
        this.ratePerSecond = ratePerSecond;
        this.burst = burst;
        this.buckets = Caffeine.newBuilder()
                .maximumSize(maximumSize)
                .expireAfterAccess(expireAfterAccess)
                .build();
    }

    /**
     * @param key  已规范化的限流键，空白直接拒绝，避免所有未知来源共用一个桶
     * @param cost 本次扣减，必须为正
     */
    public boolean allow(String key, double cost) {
        if (key == null || key.isBlank() || cost <= 0D) {
            return false;
        }
        LocalTokenBucket bucket = buckets.get(key, ignored -> new LocalTokenBucket(ratePerSecond, burst));
        return bucket != null && bucket.tryAcquire(cost);
    }
}
