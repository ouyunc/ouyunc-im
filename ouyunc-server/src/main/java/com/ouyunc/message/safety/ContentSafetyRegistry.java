package com.ouyunc.message.safety;

import com.alibaba.fastjson2.JSON;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.executor.ThreadPoolManager;
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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 租户策略 + 敏感词 AC 本地注册表。
 * <p>数据源为 Redis（管理端推送）。热路径只读本机快照；miss / Pub/Sub 失效不阻塞调用方，异步回源覆盖。
 * 加载词库时合并 {@code __global__} 与当前 appKey。</p>
 */
public final class ContentSafetyRegistry {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyRegistry.class);

    /** 进程内单例。 */
    private static final ContentSafetyRegistry INSTANCE = new ContentSafetyRegistry();

    /** 内容安全关闭时共用空自动机，避免每次 new。 */
    private static final SensitiveWordAcAutomaton EMPTY_AUTOMATON = new SensitiveWordAcAutomaton(List.of());

    /** 读 Redis Hash / String。 */
    private final StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();
    /** 保证 start 只执行一次。 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /** 正在异步加载的 appKey，避免同一租户打爆线程池。 */
    private final Set<String> reloadInFlight = ConcurrentHashMap.newKeySet();

    /** 词库和策略一起发布，避免加载失败时半更新；不 Loading，miss 异步加载。 */
    private final Cache<String, CachedSafety> safetyCache = Caffeine.newBuilder()
            .maximumSize(2_000)
            .expireAfterAccess(Duration.ofMinutes(30))
            .build();

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
     * 安排异步重载。不立刻删本地快照，避免热路径打到空词库。
     *
     * @param appKeyOrAll 租户 appKey；空或 ALL 则重载当前已缓存租户
     */
    public void invalidate(String appKeyOrAll) {
        if (StringUtils.isBlank(appKeyOrAll)
                || CacheConstant.CONTENT_SAFETY_RELOAD_ALL.equalsIgnoreCase(appKeyOrAll.trim())) {
            Set<String> keys = new HashSet<>();
            keys.addAll(safetyCache.asMap().keySet());
            if (keys.isEmpty()) {
                log.info("内容安全缓存全部重载：当前无快照");
                return;
            }
            keys.forEach(this::scheduleReload);
            log.info("内容安全缓存已安排全部异步重载 size={}", keys.size());
            return;
        }
        scheduleReload(appKeyOrAll.trim());
        log.info("内容安全缓存已安排异步重载 appKey={}", appKeyOrAll.trim());
    }

    /**
     * 取租户策略；无快照时异步加载并暂缓受理，不能把尚未读到的租户拒绝策略当作默认 MASK。
     *
     * @param appKey 租户
     * @return 非空策略
     */
    public ContentSafetyPolicy policy(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return defaultPolicy();
        }
        CachedSafety snapshot = safetyCache.getIfPresent(appKey);
        if (snapshot == null) {
            scheduleReload(appKey);
            throw new IllegalStateException("内容安全策略尚未就绪");
        }
        return snapshot.policy();
    }

    /**
     * 取合并后的敏感词自动机。
     *
     * @param appKey 租户
     * @return 非空自动机；冷 miss 异步加载并抛出暂不可用，交给统一异常策略处理
     */
    public SensitiveWordAcAutomaton matcher(String appKey) {
        if (!isEnabled() || StringUtils.isBlank(appKey)) {
            return EMPTY_AUTOMATON;
        }
        CachedSafety snapshot = safetyCache.getIfPresent(appKey);
        if (snapshot == null) {
            scheduleReload(appKey);
            throw new IllegalStateException("内容安全词库尚未就绪");
        }
        return snapshot.dict().automaton();
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

    private void scheduleReload(String appKey) {
        if (StringUtils.isBlank(appKey) || !reloadInFlight.add(appKey)) {
            return;
        }
        try {
            ThreadPoolManager.messageProcessorExecutor().execute(() -> {
                try {
                    // 全部读取成功后才发布，Redis 故障时保留旧快照，禁止用空词库覆盖。
                    CachedDict dict = loadDict(appKey);
                    ContentSafetyPolicy policy = loadPolicy(appKey);
                    safetyCache.put(appKey, new CachedSafety(dict, policy));
                } catch (Exception e) {
                    log.warn("异步加载内容安全失败，保留已有快照 appKey={}", appKey, e);
                } finally {
                    reloadInFlight.remove(appKey);
                }
            });
        } catch (RuntimeException rejected) {
            // execute 被拒绝时任务不会执行 finally，必须在提交端释放，允许下次重试加载。
            reloadInFlight.remove(appKey);
            log.warn("内容安全重载任务提交失败 appKey={}", appKey, rejected);
        }
    }

    /**
     * 从 Redis 读策略 JSON。
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
            throw new IllegalStateException("加载内容安全策略失败 appKey=" + appKey, e);
        }
    }

    /**
     * 合并全局词库 + 租户词库并编译 AC。
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
            throw new IllegalStateException("加载敏感词失败 appKey=" + appKey, e);
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

    /** 一次完整加载的词库与策略，发布后不再修改。 */
    private record CachedSafety(CachedDict dict, ContentSafetyPolicy policy) {
    }
}
