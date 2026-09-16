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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.Serializable;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: Message 上下文
 **/
public class MessageContext {
    private static final Logger log = LoggerFactory.getLogger(MessageContext.class);


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
     * 存储客户端的信息，所有客户端设备的信息都共享，生命周期与最后长的设备连接一致，设置为过期时间, 这个过期时间根据实际业务来调整，避免避免长时间不过期，占用过大内存
     */
    public static Cache<String, Serializable> localClientInfoCache = new CaffeineLocalCache<>("localClientInfoCache", Caffeine.newBuilder()
            .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
            .expireAfterWrite(NumberConstant.NUMBER_30, TimeUnit.DAYS).build(new CacheLoader<>() {
        /***
         * 获取客户端对应的客户端信息
         */
        @Override
        public @Nullable Serializable load(String appKeyIdentity) throws Exception {
            return null;
        }
    }));

    /**
     * 获取本地客户（连接在该服务器上的）端信息， 这里需要有一个类似布隆过滤器的概念，如果首次本地缓存没中，则去redis中获取，无论是否获取到，都存入本地缓存，如果获取到，则真实值存入，如果获取不到则存入一个空值或者进行标记，并设置过期时间，这样在过期时间内再次获取时，就不用请求redis了，直接走本地缓存。除非手动触发更新本地缓存（通过发布订阅）
     在过期后再次请求本地缓存，如果没有值或者标记则请求redis 然后重复以上步骤
     * @param appKey
     * @param identity
     * @return
     */
    public static ClientInfo localClientInfo(String appKey, String identity) {
        if (StringUtils.isNotBlank(identity)) {
            Serializable cacheData = localClientInfoCache.get(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity));
            if (cacheData instanceof ClientInfo clientInfo) {
                return clientInfo;
            }else if (cacheData instanceof Boolean) {
                return null;
            }else {
                // 未缓存过，则去redis中获取
                Object obj = cache.get(CacheConstant.buildRemoteClientInfoCacheKey(appKey,  identity));
                if (obj instanceof ClientInfo clientInfo) {
                    localClientInfoCache.put(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity), clientInfo);
                    return clientInfo;
                }else {
                    localClientInfoCache.put(CacheConstant.buildLocalClientInfoCacheKey(appKey, identity), Boolean.TRUE);
                }
            }
        }
        return null;
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
