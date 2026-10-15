package com.ouyunc.message.ratelimit;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.cache.config.CacheFactory;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * 登录尝试的跨节点计数。固定分钟窗口，窗口边界最多放行两倍配额，换取脚本只有一次往返。
 * Redis 失败时拒绝登录。没有来源地址时仍按身份计数；两个维度都没有则拒绝。
 */
public final class LoginAttemptLimiter {

    private static final Logger log = LoggerFactory.getLogger(LoginAttemptLimiter.class);

    /** 首次计数才设置过期，避免并发下把已有窗口提前缩短。 */
    private static final DefaultRedisScript<Long> INCREMENT_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('INCR', KEYS[1])
            if current == 1 then
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return current
            """, Long.class);

    private LoginAttemptLimiter() {
    }

    /**
     * @param clientIp 连接准入写入的来源地址，可以空；空时不按 IP 计数
     * @return true 表示本次登录可以继续做签名和配额校验
     */
    public static boolean allow(String appKey, String identity, String clientIp) {
        if (!RateLimitSettings.loginEnabled()) {
            return true;
        }
        boolean hasIp = StringUtils.isNotBlank(clientIp);
        boolean hasIdentity = StringUtils.isNotBlank(appKey) && StringUtils.isNotBlank(identity);
        if (!hasIp && !hasIdentity) {
            log.warn("登录拒绝：来源地址和身份都不可用");
            return false;
        }
        long windowMillis = RateLimitSettings.loginWindowMillis();
        long windowId = System.currentTimeMillis() / windowMillis;
        try {
            StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
            if (hasIp && exceeded(redis, CacheConstant.buildLoginRateIpCacheKey(clientIp, windowId),
                    RateLimitSettings.loginIpPerWindow())) {
                log.warn("登录拒绝：来源地址过于频繁 identity={}", identity);
                return false;
            }
            if (hasIdentity && exceeded(redis, CacheConstant.buildLoginRateIdentityCacheKey(appKey, identity, windowId),
                    RateLimitSettings.loginIdentityPerWindow())) {
                log.warn("登录拒绝：身份过于频繁 appKey={} identity={}", appKey, identity);
                return false;
            }
            return true;
        } catch (Exception ex) {
            log.warn("登录限流失败，拒绝放行 identity={}", identity, ex);
            return false;
        }
    }

    private static boolean exceeded(StringRedisTemplate redis, String key, long limit) {
        Long current = redis.execute(INCREMENT_SCRIPT, List.of(key),
                String.valueOf(RateLimitSettings.loginKeyTtlMillis()));
        return current == null || current > limit;
    }
}
