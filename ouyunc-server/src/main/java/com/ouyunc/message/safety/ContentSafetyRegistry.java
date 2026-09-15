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
 * 租户策略 + 敏感词 AC 本地注册表。
 * <p>数据源为 Redis（管理端推送）。Caffeine 写后 30 分钟过期；Pub/Sub 可立即失效。
 * 加载词库时合并 {@code __global__} 与当前 appKey。</p>
 */
public final class ContentSafetyRegistry {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyRegistry.class);

    /** 进程内单例。 */
    private static final ContentSafetyRegistry INSTANCE = new ContentSafetyRegistry();

    /** 读 Redis Hash / String。 */
    private final StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
    /** 保证 start 只执行一次。 */
    private final AtomicBoolean started = new AtomicBoolean(false);

    /** appKey → 词库自动机；miss 时从 Redis 构建。 */
    private final LoadingCache<String, CachedDict> dictCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build(this::loadDict);

    /** appKey → 策略；miss 时从 Redis JSON 解析。 */
    private final LoadingCache<String, ContentSafetyPolicy> policyCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterWrite(Duration.ofMinutes(30))
            .build(this::loadPolicy);

    /**
     * 单例构造。
     */
    private ContentSafetyRegistry() {
    }

    /**
     * @return 进程内单例
     */
    public static ContentSafetyRegistry getInstance() {
        return INSTANCE;
    }

    /**
     * 启动热更新订阅；须在 Netty bind 前调用。重复调用无副作用。
     */
    public void start() {
        if (!started.compareAndSet(false, true)) {
            return;
        }
        ContentSafetyReloadSubscriber.start(this::invalidate);
        log.info("内容安全词库/策略注册表已启动");
    }

    /**
     * 使本地缓存失效。
     *
     * @param appKeyOrAll 租户 appKey；空或 ALL 则全部失效
     */
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

    /**
     * 取租户策略；无 Redis 配置时回退 YAML 默认。
     *
     * @param appKey 租户
     * @return 非空策略
     */
    public ContentSafetyPolicy policy(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return defaultPolicy();
        }
        ContentSafetyPolicy policy = policyCache.get(appKey);
        return policy == null ? defaultPolicy() : policy;
    }

    /**
     * 取合并后的敏感词自动机。
     *
     * @param appKey 租户
     * @return 非空自动机（可能无词）
     */
    public SensitiveWordAcAutomaton matcher(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return new SensitiveWordAcAutomaton(List.of());
        }
        CachedDict dict = dictCache.get(appKey);
        return dict == null ? new SensitiveWordAcAutomaton(List.of()) : dict.automaton();
    }

    /**
     * 读取 YAML 总开关；读取失败时默认开启，避免误关。
     *
     * @return true 启用内容安全
     */
    public boolean isEnabled() {
        try {
            return MessageServerContext.serverProperties().isContentSafetyEnable();
        } catch (Exception e) {
            return true;
        }
    }

    /**
     * YAML 默认策略（无租户 JSON 时使用）。
     *
     * @return 默认策略
     */
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

    /**
     * Caffeine load：从 Redis 读策略 JSON。
     *
     * @param appKey 租户
     * @return 策略
     */
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

    /**
     * Caffeine load：合并全局词库 + 租户词库并编译 AC。
     *
     * @param appKey 租户
     * @return 本地词库快照
     */
    private CachedDict loadDict(String appKey) {
        Set<String> words = new HashSet<>();
        loadWordsInto(CacheConstant.CONTENT_SAFETY_GLOBAL_APP_KEY, words);
        loadWordsInto(appKey, words);
        long version = readVersion(appKey);
        log.debug("加载敏感词 appKey={} size={} version={}", appKey, words.size(), version);
        return new CachedDict(version, new SensitiveWordAcAutomaton(words));
    }

    /**
     * 将指定 appKey 的 Redis Hash field 并入词集合。
     *
     * @param appKey 租户或 __global__
     * @param words  输出集合
     */
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

    /**
     * 读取租户词库版本号，失败返回 0。
     *
     * @param appKey 租户
     * @return 版本
     */
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

    /**
     * 本地词库快照。
     *
     * @param version   Redis 版本号（调试用）
     * @param automaton AC 自动机
     */
    private record CachedDict(long version, SensitiveWordAcAutomaton automaton) {
    }
}
