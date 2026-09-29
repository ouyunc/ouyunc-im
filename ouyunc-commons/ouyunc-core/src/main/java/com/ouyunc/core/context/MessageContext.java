package com.ouyunc.core.context;


import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.ClientInfo;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.properties.MessageProperties;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import com.ouyunc.id.CosIdSnowflakeIdGenerator;
import com.ouyunc.id.IdGenerator;
import io.netty.util.internal.ThreadLocalRandom;
import org.apache.commons.lang3.StringUtils;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.redisson.api.RedissonClient;
import org.redisson.api.RedissonReactiveClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: Message 上下文
 **/
public class MessageContext {

    private static final Logger log = LoggerFactory.getLogger(MessageContext.class);


    /**
     * 分布式锁redisson
     */
    public static final RedissonClient redissonClient = CacheFactory.REDISSON.instance();


    /**
     * 响应式分布式锁redisson
     */
    public static RedissonReactiveClient reactiveRedissonClient = CacheFactory.REACTIVE_REDISSON.instance();



    /**
     * 消息事件多播器（由服务端直接创建并注入；
     * 当前实现为 {@link com.ouyunc.core.listener.DisruptorMessageEventMulticaster}）。
     */
    public static MessageEventMulticaster messageEventMulticaster;

    /**
     * message 基础消息属性配置类
     * */
    public static MessageProperties messageProperties;

    /**
     * 统一获取 Redis 消息正文热数据 TTL。
     * <p>服务启动前或独立仓储测试尚未注入配置时使用默认值，避免业务调用点直接解引用全局配置。</p>
     */
    public static long messageHotDataTtlMillis() {
        return messageProperties == null
                ? MessageConstant.CACHE_MESSAGE_HOT_DEFAULT_TTL_SECONDS * 1_000L
                : messageProperties.getHotDataTtlMillis();
    }

    /**
     * QoS 是否开启（仓库与处理器统一入口）
     */
    public static boolean isQosEnable() {
        return messageProperties != null && messageProperties.isQosEnable();
    }


    /**
     * 缓存
     */
    public static final Cache<String, ?> cache = new RedisDistributedCache<>(CacheFactory.REDIS.instance(), CacheFactory.STRING_REDIS.instance());


    /**
     * 全局 id 生成器
     */
    private static IdGenerator idGenerator = CosIdSnowflakeIdGenerator.INSTANCE;

    /**
     * 获取全局 id 生成器
     */
    public static IdGenerator idGenerator () {
        return idGenerator;
    }

    /**
     * 设置全局 id 生成器
     */
    public static void setIdGenerator (IdGenerator newIdGenerator) {
        idGenerator = newIdGenerator;
    }



    /**
     * 客户端信息统一查询结果缓存。命中与未命中必须放在同一个 key 空间，避免两个独立缓存并发回填后
     * “未命中标记”长期遮住已经加载成功的 ClientInfo。
     * <p>正结果 5 分钟兜底刷新，负结果 1 分钟重试；Pub/Sub 仍负责配置变更后的即时失效。</p>
     */
    public static final Cache<String, ClientInfoLookup> localClientInfoCache = CaffeineLocalCache.wrap(
            "localClientInfoCache",
            Caffeine.newBuilder()
                    .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
                    .expireAfter(new Expiry<String, ClientInfoLookup>() {
                        @Override
                        public long expireAfterCreate(String key, ClientInfoLookup value, long currentTime) {
                            return clientInfoExpireNanos(value);
                        }

                        @Override
                        public long expireAfterUpdate(String key, ClientInfoLookup value,
                                                      long currentTime, long currentDuration) {
                            return clientInfoExpireNanos(value);
                        }

                        @Override
                        public long expireAfterRead(String key, ClientInfoLookup value,
                                                    long currentTime, long currentDuration) {
                            return currentDuration;
                        }
                    })
                    .recordStats()
                    .build());

    /**
     * 获取连接在本机视角下的客户端信息。Caffeine 原生 get(key, mappingFunction) 对同一 key 合并并发加载，
     * 冷启动时每个 identity 只会有一个线程访问 Redis。
     */
    public static ClientInfo localClientInfo(String appKey, String identity) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return null;
        }
        String localKey = CacheConstant.buildLocalClientInfoCacheKey(appKey, identity);
        com.github.benmanes.caffeine.cache.Cache<String, ClientInfoLookup> nativeCache =
                localClientInfoCache.instance();
        ClientInfoLookup lookup = nativeCache.get(localKey, ignored -> loadClientInfo(appKey, identity));
        return lookup == null ? null : lookup.clientInfo();
    }

    /**
     * 删除本机客户端信息查询结果，下次 {@link #localClientInfo} 重新读 Redis。
     */
    public static void evictLocalClientInfo(String appKey, String identity) {
        if (StringUtils.isAnyBlank(appKey, identity)) {
            return;
        }
        String localKey = CacheConstant.buildLocalClientInfoCacheKey(appKey, identity);
        localClientInfoCache.delete(localKey);
    }

    private static ClientInfoLookup loadClientInfo(String appKey, String identity) {
        Object value = cache.get(CacheConstant.buildRemoteClientInfoCacheKey(appKey, identity));
        return value instanceof ClientInfo clientInfo
                ? ClientInfoLookup.found(clientInfo)
                : ClientInfoLookup.missing();
    }

    private static long clientInfoExpireNanos(ClientInfoLookup lookup) {
        int seconds = lookup != null && lookup.found()
                ? MessageConstant.CLIENT_INFO_LOCAL_HIT_EXPIRE_SECONDS
                : MessageConstant.CLIENT_INFO_LOCAL_MISS_EXPIRE_SECONDS;
        return TimeUnit.SECONDS.toNanos(seconds);
    }

    /**
     * 显式表达 Redis 中“存在/不存在”，避免 Caffeine 禁止 null value 时再引入第二份负缓存。
     */
    public record ClientInfoLookup(boolean found, ClientInfo clientInfo) {
        private static final ClientInfoLookup MISSING = new ClientInfoLookup(false, null);

        public static ClientInfoLookup found(ClientInfo clientInfo) {
            return new ClientInfoLookup(true, clientInfo);
        }

        public static ClientInfoLookup missing() {
            return MISSING;
        }
    }

    /**
     * 业务侧统一发布入口；由服务端启动阶段注入事件多播器。
     *
     * @param async 是否异步发布事件 true-异步（走 Disruptor），false-同步
     */
    public static void publishEvent(MessageEvent event, boolean async) {
        if (messageEventMulticaster != null) {
            messageEventMulticaster.multicastEvent(event, async);
        }
    }

    /**
     * @Author fzx
     * @Description 同步发布IM事件
     * @param event IMEvent事件的子类
     */
    public static void publishEvent(MessageEvent event) {
        if (messageEventMulticaster != null) {
            messageEventMulticaster.multicastEvent(event, false);
        }
    }


    /**
     * @Author fzx
     * @Description 同步发布IM事件
     * @param event IMEvent事件的子类
     */
    public static void publishEventWithExecutor(MessageEvent event) {
        if (messageEventMulticaster != null) {
            messageEventMulticaster.multicastEventWithExecutor(event, false);
        }
    }

    /**
     * @Author fzx
     * @Description 同步发布IM事件
     * @param event IMEvent事件的子类
     */
    public static void publishEventWithExecutor(MessageEvent event, boolean async) {
        if (messageEventMulticaster != null) {
            messageEventMulticaster.multicastEventWithExecutor(event, async);
        }
    }




    /**
     * 好友配置的映射缓存
     */
    public static final Cache<String, FriendEntity> friendEntityCache = new CaffeineLocalCache<>("friendEntity", Caffeine.newBuilder()
            // 最大条目数：100万（预留20%冗余，避免频繁淘汰）
            .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
            // 淘汰策略：LRU（最近最少使用）→ 适合热点数据集中的场景
            // 若热点分散，可改用 LFU（最少频率使用）：.expireAfterWrite(...) + .weigher(...)
            .evictionListener((key, value, cause) -> {
                // 监控淘汰原因（如容量满、过期），用于调优
                if (cause == RemovalCause.SIZE) {
                    // 容量满导致淘汰：可能需要扩容或优化数据大小
                    log.warn("好友缓存因容量满被淘汰，key={}, cause={}", key, cause);
                } else if (cause == RemovalCause.EXPIRED) {
                    // 过期淘汰：正常现象，无需告警
                    log.debug("好友缓存过期淘汰，key={}", key);
                }
            })
            // 过期时间：区分数据类型（如好友5分钟，群成员10分钟）
            .expireAfter(new Expiry<String, FriendEntity>() {

                // 1. 新条目创建后：基础5分钟 + 随机偏移（避免雪崩）
                @Override
                public long expireAfterCreate(String key, FriendEntity value, long currentTime) {
                    return getRandomExpireNanos(); // 5分钟基础时间
                }

                // 2. 条目更新后：重置为5分钟 + 随机偏移（更新后延长有效期）
                @Override
                public long expireAfterUpdate(String key, FriendEntity value, long currentTime, long currentDuration) {
                    return getRandomExpireNanos(); // 更新后重新计算过期时间
                }

                // 3. 条目读取后：不改变过期时间（读操作不续期）
                @Override
                public long expireAfterRead(String key, FriendEntity value, long currentTime, long currentDuration) {
                    return currentDuration; // 保持原剩余过期时间
                }

                // 工具方法：生成带随机偏移的过期时间（单位：纳秒）
                private long getRandomExpireNanos() {
                    long baseNanos = TimeUnit.SECONDS.toNanos(MessageConstant.RELATION_ENTITY_LOCAL_CACHE_EXPIRE_SECONDS);
                    // 随机±5秒偏移，避免雪崩
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_5, NumberConstant.NUMBER_6));
                    return baseNanos + randomNanos;
                }
            })
            // 记录统计信息（命中率、淘汰数等）
            .recordStats()
            // 加载函数：缓存未命中时的加载逻辑（如查Redis/DB）
            .build(new CacheLoader<String, FriendEntity>() {
                @Override
                public @Nullable FriendEntity load(String s) throws Exception {
                    return null;
                }
            }));





    /**
     * 群组配置的映射缓存
     */
    public static final Cache<String, GroupEntity> groupEntityCache = new CaffeineLocalCache<>("groupEntity", Caffeine.newBuilder()
            // 最大条目数：100万（预留20%冗余，避免频繁淘汰）
            .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
            // 淘汰策略：LRU（最近最少使用）→ 适合热点数据集中的场景
            // 若热点分散，可改用 LFU（最少频率使用）：.expireAfterWrite(...) + .weigher(...)
            .evictionListener((key, value, cause) -> {
                // 监控淘汰原因（如容量满、过期），用于调优
                if (cause == RemovalCause.SIZE) {
                    // 容量满导致淘汰：可能需要扩容或优化数据大小
                    log.warn("群缓存因容量满被淘汰，key={}, cause={}", key, cause);
                } else if (cause == RemovalCause.EXPIRED) {
                    // 过期淘汰：正常现象，无需告警
                    log.debug("群缓存过期淘汰，key={}", key);
                }
            })
            // 过期时间：区分数据类型（群成员10分钟）
            .expireAfter(new Expiry<String, GroupEntity>() {

                // 1. 新条目创建后：基础10分钟 + 随机偏移（避免雪崩）
                @Override
                public long expireAfterCreate(String key, GroupEntity value, long currentTime) {
                    return getRandomExpireNanos(); // 5分钟基础时间
                }

                // 2. 条目更新后：重置为10分钟 + 随机偏移（更新后延长有效期）
                @Override
                public long expireAfterUpdate(String key, GroupEntity value, long currentTime, long currentDuration) {
                    return getRandomExpireNanos(); // 更新后重新计算过期时间
                }

                // 3. 条目读取后：不改变过期时间（读操作不续期）
                @Override
                public long expireAfterRead(String key, GroupEntity value, long currentTime, long currentDuration) {
                    return currentDuration; // 保持原剩余过期时间
                }

                // 工具方法：生成带随机偏移的过期时间（单位：纳秒）
                private long getRandomExpireNanos() {
                    long baseNanos = TimeUnit.SECONDS.toNanos(MessageConstant.GROUP_POLICY_LOCAL_CACHE_EXPIRE_SECONDS);
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_1, NumberConstant.NUMBER_2));
                    return baseNanos + randomNanos;
                }
            })
            // 记录统计信息（命中率、淘汰数等）
            .recordStats()
            // 加载函数：缓存未命中时的加载逻辑（如查Redis/DB）
            .build(new CacheLoader<String, GroupEntity>() {
                @Override
                public @Nullable GroupEntity load(String s) throws Exception {
                    return null;
                }
            }));




    /**
     * 群成员配置的映射缓存
     */
    public static final Cache<String, GroupUserEntity> groupUserEntityCache = new CaffeineLocalCache<>("groupUserEntity", Caffeine.newBuilder()
            .maximumWeight(MessageConstant.GROUP_USER_ENTITY_CACHE_MAX_WEIGHT)
            .weigher((String key, GroupUserEntity value) -> approximateEntityWeight(key, 384))
            // 淘汰策略：LRU（最近最少使用）→ 适合热点数据集中的场景
            // 若热点分散，可改用 LFU（最少频率使用）：.expireAfterWrite(...) + .weigher(...)
            .evictionListener((key, value, cause) -> {
                // 监控淘汰原因（如容量满、过期），用于调优
                if (cause == RemovalCause.SIZE) {
                    // 容量满导致淘汰：可能需要扩容或优化数据大小
                    log.warn("群成员缓存因容量满被淘汰，key={}, cause={}", key, cause);
                } else if (cause == RemovalCause.EXPIRED) {
                    // 过期淘汰：正常现象，无需告警
                    log.debug("群成员缓存过期淘汰，key={}", key);
                }
            })
            // 过期时间：区分数据类型（群成员10分钟）
            .expireAfter(new Expiry<String, GroupUserEntity>() {

                // 1. 新条目创建后：基础10分钟 + 随机偏移（避免雪崩）
                @Override
                public long expireAfterCreate(String key, GroupUserEntity value, long currentTime) {
                    return getRandomExpireNanos(); // 5分钟基础时间
                }

                // 2. 条目更新后：重置为10分钟 + 随机偏移（更新后延长有效期）
                @Override
                public long expireAfterUpdate(String key, GroupUserEntity value, long currentTime, long currentDuration) {
                    return getRandomExpireNanos(); // 更新后重新计算过期时间
                }

                // 3. 条目读取后：不改变过期时间（读操作不续期）
                @Override
                public long expireAfterRead(String key, GroupUserEntity value, long currentTime, long currentDuration) {
                    return currentDuration; // 保持原剩余过期时间
                }

                // 工具方法：生成带随机偏移的过期时间（单位：纳秒）
                private long getRandomExpireNanos() {
                    long baseNanos = TimeUnit.SECONDS.toNanos(MessageConstant.RELATION_ENTITY_LOCAL_CACHE_EXPIRE_SECONDS);
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_5, NumberConstant.NUMBER_6));
                    return baseNanos + randomNanos;
                }
            })
            // 记录统计信息（命中率、淘汰数等）
            .recordStats()
            // 加载函数：缓存未命中时的加载逻辑（如查Redis/DB）
            .build(new CacheLoader<String, GroupUserEntity>() {
                @Override
                public @Nullable GroupUserEntity load(String s) throws Exception {
                    return null;
                }
            }));


    /**
     * 用户实体的映射缓存
     */
    public static final Cache<String, UserEntity> userEntityCache = new CaffeineLocalCache<>("userEntity", Caffeine.newBuilder()
            // 按近似字节预算，避免大对象 + 固定条数失控
            .maximumWeight(MessageConstant.USER_ENTITY_CACHE_MAX_WEIGHT)
            .weigher((String key, UserEntity value) -> approximateEntityWeight(key, 512))
            // 淘汰策略：LRU（最近最少使用）→ 适合热点数据集中的场景
            .evictionListener((key, value, cause) -> {
                // 监控淘汰原因（如容量满、过期），用于调优
                if (cause == RemovalCause.SIZE) {
                    // 容量满导致淘汰：可能需要扩容或优化数据大小
                    log.warn("用户缓存因容量满被淘汰，key={}, cause={}", key, cause);
                } else if (cause == RemovalCause.EXPIRED) {
                    // 过期淘汰：正常现象，无需告警
                    log.debug("用户缓存过期淘汰，key={}", key);
                }
            })
            // 过期时间：用户信息30分钟
            .expireAfter(new Expiry<String, UserEntity>() {

                // 1. 新条目创建后：基础30分钟 + 随机偏移（避免雪崩）
                @Override
                public long expireAfterCreate(String key, UserEntity value, long currentTime) {
                    return getRandomExpireNanos();
                }

                // 2. 条目更新后：重置为30分钟 + 随机偏移（更新后延长有效期）
                @Override
                public long expireAfterUpdate(String key, UserEntity value, long currentTime, long currentDuration) {
                    return getRandomExpireNanos();
                }

                // 3. 条目读取后：不改变过期时间（读操作不续期）
                @Override
                public long expireAfterRead(String key, UserEntity value, long currentTime, long currentDuration) {
                    return currentDuration; // 保持原剩余过期时间
                }

                // 工具方法：生成带随机偏移的过期时间（单位：纳秒）
                private long getRandomExpireNanos() {
                    long baseNanos = TimeUnit.MINUTES.toNanos(NumberConstant.NUMBER_30);
                    // 随机±2分钟偏移
                    long randomNanos = TimeUnit.MINUTES.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_2, NumberConstant.NUMBER_3));
                    return baseNanos + randomNanos;
                }
            })
            // 记录统计信息（命中率、淘汰数等）
            .recordStats()
            // 加载函数：缓存未命中时的加载逻辑（如查Redis/DB）
            .build(new CacheLoader<String, UserEntity>() {
                @Override
                public @Nullable UserEntity load(String s) throws Exception {
                    return null;
                }
            }));

    /**
     * 群成员 identity 列表。命中直接用，不 GET 版本；入群/退群靠 Pub/Sub 立刻失效，TTL 仅兜底丢通知。
     * 权重≈ key + 成员 id 总长，大群 Set 占更多预算。
     */
    public static final Cache<String, Set<String>> groupUserIdentityCache = CaffeineLocalCache.wrap(
            "groupUserIdentity",
            Caffeine.newBuilder()
                    .maximumWeight(MessageConstant.GROUP_MEMBER_IDENTITY_CACHE_MAX_WEIGHT)
                    .weigher((String key, Set<String> members) -> approximateMemberSetWeight(key, members))
                    .expireAfterWrite(MessageConstant.GROUP_MEMBER_IDENTITY_CACHE_EXPIRE_SECONDS, TimeUnit.SECONDS)
                    .recordStats()
                    .build());

    /** 实体缓存权重近似：key 字符 + 固定载荷估算 */
    private static int approximateEntityWeight(String key, int payloadBytes) {
        int keyBytes = key == null ? 0 : key.length() * 2;
        return Math.max(1, keyBytes + payloadBytes);
    }

    private static int approximateMemberSetWeight(String key, Set<String> members) {
        int weight = key == null ? 0 : key.length() * 2;
        if (members != null) {
            for (String id : members) {
                weight += id == null ? 8 : (id.length() * 2 + 16);
            }
        }
        return Math.max(1, weight);
    }

}
