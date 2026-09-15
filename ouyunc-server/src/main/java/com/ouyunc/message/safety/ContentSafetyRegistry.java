package com.ouyunc.message.safety;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.model.ContentSafetyPolicy;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 租户策略 + 敏感词 AC 本地缓存；数据源为 Redis（管理端推送）。
 */
public final class ContentSafetyRegistry {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyRegistry.class);

    private static final ContentSafetyRegistry INSTANCE = new ContentSafetyRegistry();

    private final StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
    private final AtomicBoolean started = new AtomicBoolean(false);

    private final LoadingCache<String, CachedDict> dictCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build(this::loadDict);

    private final LoadingCache<String, ContentSafetyPolicy> policyCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build(this::loadPolicy);

    private ContentSafetyRegistry() {
    }

    public static ContentSafetyRegistry getInstance() {
        return INSTANCE;
    }

    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ContentSafetyReloadSubscriber.start(this::invalidate);
        log.info("内容安全词库/策略注册表已启动");
    }

    public void invalidate(String appKeyOrAll) {
        if (StringUtils.isBlank(appKeyOrAll) || "ALL".equalsIgnoreCase(appKeyOrAll.trim())) {
            dictCache.invalidateAll();
            policyCache.invalidateAll();
            log.info("内容安全缓存已全部失效");
            return;
        }
        String appKey = appKeyOrAll.trim();
        dictCache.invalidate(appKey);
        policyCache.invalidate(appKey);
        log.info("内容安全缓存已失效 appKey={}", appKey);
    }

    public ContentSafetyPolicy policy(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return defaultPolicy();
        }
        ContentSafetyPolicy policy = policyCache.get(appKey);
        return policy == null ? defaultPolicy() : policy;
    }

    public SensitiveWordAcAutomaton matcher(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return new SensitiveWordAcAutomaton(List.of());
        }
        CachedDict dict = dictCache.get(appKey);
        return dict == null ? new SensitiveWordAcAutomaton(List.of()) : dict.automaton();
    }

    public boolean isEnabled() {
        try {
            return MessageServerContext.serverProperties().isContentSafetyEnable();
        } catch (Exception e) {
            return true;
        }
    }

    private ContentSafetyPolicy defaultPolicy() {
        ContentSafetyPolicy policy = ContentSafetyPolicy.defaults();
        try {
            String configured = MessageServerContext.serverProperties().getContentSafetyDefaultTextAction();
            policy.setTextAction(ContentSafetyAction.from(configured, ContentSafetyAction.MASK));
            String media = MessageServerContext.serverProperties().getContentSafetyDefaultMediaAction();
            policy.setMediaAction(ContentSafetyAction.from(media, ContentSafetyAction.SEND_THEN_REVIEW));
        } catch (Exception ignored) {
            // keep MASK / SEND_THEN_REVIEW
        }
        return policy;
    }

    private ContentSafetyPolicy loadPolicy(String appKey) {
        try {
            String json = stringRedis.opsForValue().get(CacheConstant.buildContentSafetyPolicyCacheKey(appKey));
            if (StringUtils.isBlank(json)) {
                return defaultPolicy();
            }
            ContentSafetyPolicy policy = JSON.parseObject(json, ContentSafetyPolicy.class);
            return policy == null ? defaultPolicy() : policy;
        } catch (Exception e) {
            log.warn("加载内容安全策略失败 appKey={}，使用默认", appKey, e);
            return defaultPolicy();
        }
    }

    private CachedDict loadDict(String appKey) {
        Set<String> words = new HashSet<>();
        loadWordsInto(CacheConstant.CONTENT_SAFETY_GLOBAL_APP_KEY, words);
        loadWordsInto(appKey, words);
        long version = readVersion(appKey);
        log.debug("加载敏感词 appKey={} size={} version={}", appKey, words.size(), version);
        return new CachedDict(version, new SensitiveWordAcAutomaton(words));
    }

    private void loadWordsInto(String appKey, Set<String> words) {
        try {
            Map<Object, Object> entries = stringRedis.opsForHash()
                    .entries(CacheConstant.buildContentSafetyWordsCacheKey(appKey));
            if (entries == null || entries.isEmpty()) {
                return;
            }
            for (Object key : entries.keySet()) {
                if (key != null) {
                    String word = String.valueOf(key).trim();
                    if (!word.isEmpty()) {
                        words.add(word);
                    }
                }
            }
        } catch (Exception e) {
            log.warn("加载敏感词失败 appKey={}", appKey, e);
        }
    }

    private long readVersion(String appKey) {
        try {
            String v = stringRedis.opsForValue().get(CacheConstant.buildContentSafetyVersionCacheKey(appKey));
            if (StringUtils.isBlank(v)) {
                return 0L;
            }
            return Long.parseLong(v.trim());
        } catch (Exception e) {
            return 0L;
        }
    }

    private record CachedDict(long version, SensitiveWordAcAutomaton automaton) {
    }
}
