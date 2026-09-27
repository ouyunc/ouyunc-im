package com.ouyunc.message.cache;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 业务空闲提示文案：L1 Caffeine（按 Redis key）→ L2 Redis Hash。
 * <p>热路径只读本机；miss 时 HGETALL。Redis 异常不写负缓存，下次再回源。</p>
 */
public final class IdleNotifyTextCache {

    private static final Logger log = LoggerFactory.getLogger(IdleNotifyTextCache.class);

    /** 全平台默认文案在本机缓存中的占位 appKey。 */
    public static final String GLOBAL_CACHE_KEY = CacheConstant.CONTENT_SAFETY_GLOBAL_APP_KEY;

    private static final IdleNotifyTextCache INSTANCE = new IdleNotifyTextCache();

    private final StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();

    /**
     * Redis Hash 快照。key=租户 appKey 或 {@link #GLOBAL_CACHE_KEY}。
     */
    public final Cache<String, Map<String, String>> localCache = CaffeineLocalCache.wrap(
            "idleNotifyTextCache",
            Caffeine.newBuilder()
                    .maximumSize(2_000)
                    .expireAfterWrite(MessageConstant.IDLE_NOTIFY_TEXT_LOCAL_EXPIRE_MINUTES, TimeUnit.MINUTES)
                    .recordStats()
                    .build());

    private IdleNotifyTextCache() {
    }

    public static IdleNotifyTextCache getInstance() {
        return INSTANCE;
    }

    /**
     * 启动热更新订阅；须在 Netty bind 前调用。重复调用无副作用。
     */
    public void start() {
        IdleNotifyTextReloadSubscriber.start(this::invalidate);
    }

    /**
     * 取租户 Hash；未命中则读 Redis 并回填本机。
     */
    public Map<String, String> getTenant(String appKey) {
        if (StringUtils.isBlank(appKey)) {
            return Collections.emptyMap();
        }
        return load(appKey.trim(), CacheConstant.buildIdleNotifyTextCacheKey(appKey.trim()));
    }

    /**
     * 取全平台默认 Hash。
     */
    public Map<String, String> getGlobal() {
        return load(GLOBAL_CACHE_KEY, CacheConstant.buildIdleNotifyTextGlobalCacheKey());
    }

    /**
     * 管理端改文案后可主动失效本机快照；不删 Redis。
     */
    public void invalidate(String appKeyOrAll) {
        if (StringUtils.isBlank(appKeyOrAll) || "ALL".equalsIgnoreCase(appKeyOrAll.trim())) {
            localCache.deleteAll(Set.copyOf(localCache.asMap().keySet()));
            return;
        }
        localCache.delete(appKeyOrAll.trim());
    }

    private Map<String, String> load(String cacheKey, String redisKey) {
        Map<String, String> present = localCache.get(cacheKey);
        if (present != null) {
            return present;
        }
        try {
            Map<Object, Object> raw = stringRedis.opsForHash().entries(redisKey);
            Map<String, String> copied = toStringMap(raw);
            // 空 Hash 不进 L1，避免首次空读把管理端刚写入的文案挡住 10 分钟
            if (!copied.isEmpty()) {
                localCache.put(cacheKey, copied);
            }
            return copied;
        } catch (Exception e) {
            log.warn("读取业务空闲文案 Redis 失败, redisKey={}: {}", redisKey, e.getMessage());
            return Collections.emptyMap();
        }
    }

    private static Map<String, String> toStringMap(Map<Object, Object> raw) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, String> copied = new HashMap<>(raw.size());
        for (Map.Entry<Object, Object> e : raw.entrySet()) {
            if (e.getKey() == null || e.getValue() == null) {
                continue;
            }
            String field = String.valueOf(e.getKey()).trim();
            String val = String.valueOf(e.getValue()).trim();
            if (field.isEmpty() || val.isEmpty()) {
                continue;
            }
            copied.put(field, val);
        }
        return copied.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(copied);
    }
}
