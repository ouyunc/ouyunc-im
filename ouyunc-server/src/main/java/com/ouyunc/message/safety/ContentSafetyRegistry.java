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
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 租户策略 + 敏感词 AC 本地注册表。
 * <p>数据源为 Redis（管理端推送）。热路径只读本机快照。冷 miss 在工作线程上等同一次加载，
 * 超时仍暂缓受理，不能把还没读到的租户策略当成默认 MASK。加载词库时合并 {@code __global__} 与当前 appKey。</p>
 */
public final class ContentSafetyRegistry {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyRegistry.class);

    /** 进程内单例。 */
    private static final ContentSafetyRegistry INSTANCE = new ContentSafetyRegistry();

    /** 内容安全关闭时共用空自动机，避免每次 new。 */
    private static final SensitiveWordAcAutomaton EMPTY_AUTOMATON = new SensitiveWordAcAutomaton(List.of());

    /** 冷 miss 时等待 Redis 回源的上限。检查跑在工作线程，不占 Netty EventLoop。 */
    private static final Duration LOAD_WAIT = Duration.ofMillis(800);

    /** 本地快照上限。每个租户一份词库加策略，超出后淘汰最久未访问的。 */
    private static final int SAFETY_CACHE_MAXIMUM_SIZE = 20_000;

    /** 读 Redis Hash / String。 */
    private final StringRedisTemplate stringRedis = CacheFactory.STRING_REDIS.instance();

    /** 保证 start 只执行一次。 */
    private final AtomicBoolean started = new AtomicBoolean(false);
    /** 同一租户只保留一次在途加载，调用方等待这份结果，避免首条消息在快照写入前被打回。 */
    private final ConcurrentHashMap<String, CompletableFuture<CachedSafety>> loading = new ConcurrentHashMap<>();

    /** 词库和策略一起发布，避免加载失败时半更新；不 Loading，miss 异步加载。 */
    private final Cache<String, CachedSafety> safetyCache = Caffeine.newBuilder()
            .maximumSize(SAFETY_CACHE_MAXIMUM_SIZE)
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
     * 在对外接客前同步装入已知租户的词库和策略。
     * Redis 失败只记日志，不阻断启动；对应租户的首条文本会再等一次异步加载。
     *
     * @param appKeys 已预热的租户，空集合直接返回
     */
    public void warmup(Collection<String> appKeys) {
        if (!isEnabled() || appKeys == null || appKeys.isEmpty()) {
            return;
        }
        int loaded = 0;
        for (String raw : appKeys) {
            if (StringUtils.isBlank(raw)) {
                continue;
            }
            String appKey = raw.trim();
            try {
                safetyCache.put(appKey, loadSnapshot(appKey));
                loaded++;
            } catch (Exception e) {
                log.warn("内容安全预热失败，该租户首条文本将等待加载 appKey={}", appKey, e);
            }
        }
        log.info("内容安全预热完成 loaded={} requested={}", loaded, appKeys.size());
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
        return snapshot(appKey).policy();
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
        return snapshot(appKey).dict().automaton();
    }

    /**
     * 先读本地快照。没有快照时等待同一次 Redis 加载，超时才抛「尚未就绪」让调用方暂缓受理。
     */
    private CachedSafety snapshot(String appKey) {
        CachedSafety snapshot = safetyCache.getIfPresent(appKey);
        if (snapshot != null) {
            return snapshot;
        }
        CompletableFuture<CachedSafety> future = scheduleReload(appKey);
        try {
            return future.get(LOAD_WAIT.toMillis(), TimeUnit.MILLISECONDS);
        } catch (TimeoutException e) {
            snapshot = safetyCache.getIfPresent(appKey);
            if (snapshot != null) {
                return snapshot;
            }
            throw new IllegalStateException("内容安全策略尚未就绪");
        } catch (ExecutionException e) {
            snapshot = safetyCache.getIfPresent(appKey);
            if (snapshot != null) {
                return snapshot;
            }
            Throwable cause = e.getCause() == null ? e : e.getCause();
            if (cause instanceof RuntimeException runtime) {
                throw runtime;
            }
            throw new IllegalStateException("内容安全策略尚未就绪", cause);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("内容安全策略加载被中断");
        }
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

    private CompletableFuture<CachedSafety> scheduleReload(String appKey) {
        return loading.compute(appKey, (key, existing) -> {
            if (existing != null && !existing.isDone()) {
                return existing;
            }
            CompletableFuture<CachedSafety> future = new CompletableFuture<>();
            try {
                ThreadPoolManager.messageProcessorExecutor().execute(() -> completeLoad(key, future));
            } catch (RuntimeException rejected) {
                // 提交失败时任务不会跑 finally，当场结束 future，下次调用可以再提交。
                future.completeExceptionally(rejected);
                log.warn("内容安全重载任务提交失败 appKey={}", key, rejected);
            }
            return future;
        });
    }

    /** 词库和策略都读成功才发布。Redis 故障时不写入，已有快照保持不变。 */
    private void completeLoad(String appKey, CompletableFuture<CachedSafety> future) {
        try {
            CachedSafety snapshot = loadSnapshot(appKey);
            safetyCache.put(appKey, snapshot);
            future.complete(snapshot);
        } catch (Exception e) {
            log.warn("异步加载内容安全失败，保留已有快照 appKey={}", appKey, e);
            future.completeExceptionally(e);
        } finally {
            loading.remove(appKey, future);
        }
    }

    private CachedSafety loadSnapshot(String appKey) {
        return new CachedSafety(loadDict(appKey), loadPolicy(appKey));
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
        // 空词库会让文本全部放行。打 info，避免只在 debug 里才能发现 Redis 没推词。
        log.info("加载敏感词 appKey={} size={} version={}", appKey, words.size(), version);
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
