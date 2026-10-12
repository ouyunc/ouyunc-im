package com.ouyunc.message.validator;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.config.CacheFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

/**
 * 登录 createTime 一次性消费。签名校验通过后才写入，避免未知密钥的请求占满 nonce。
 * Redis 失败时拒绝登录，不把重放窗口重新打开。
 */
public final class LoginNonceStore {

    private static final Logger log = LoggerFactory.getLogger(LoginNonceStore.class);

    private LoginNonceStore() {
    }

    public static boolean consume(String appKey, String identity, long createTime) {
        String key = CacheConstant.buildLoginNonceCacheKey(appKey, identity, createTime);
        try {
            StringRedisTemplate redis = CacheFactory.STRING_REDIS.instance();
            Boolean stored = redis.opsForValue().setIfAbsent(
                    key, "1", Duration.ofMillis(MessageConstant.LOGIN_SIGNATURE_CREATE_TIME_SKEW_MS));
            if (!Boolean.TRUE.equals(stored)) {
                log.warn("登录拒绝：签名已使用 appKey={} identity={}", appKey, identity);
                return false;
            }
            return true;
        } catch (Exception e) {
            log.warn("登录拒绝：nonce 写入失败 identity={}", identity, e);
            return false;
        }
    }
}
