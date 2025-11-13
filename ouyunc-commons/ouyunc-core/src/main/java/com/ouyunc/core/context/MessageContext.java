package com.ouyunc.core.context;


import com.github.benmanes.caffeine.cache.CacheLoader;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.Expiry;
import com.github.benmanes.caffeine.cache.RemovalCause;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.cache.Cache;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.cache.distributed.redis.RedisDistributedCache;
import com.ouyunc.cache.local.caffeine.CaffeineLocalCache;
import com.ouyunc.core.generator.IdGenerator;
import com.ouyunc.core.generator.SnowflakeIdGenerator;
import com.ouyunc.core.listener.MessageEventMulticaster;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.properties.MessageProperties;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.UserEntity;
import io.netty.util.internal.ThreadLocalRandom;
import org.checkerframework.checker.nullness.qual.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * @Author fzx
 * @Description: Message 上下文
 **/
public class MessageContext {
    private static final Logger log = LoggerFactory.getLogger(MessageContext.class);

    /**
     * message 事件多播器
     * */
    public static MessageEventMulticaster messageEventMulticaster;

    /**
     * message 基础消息属性配置类
     * */
    public static MessageProperties messageProperties;


    /**
     * 缓存
     */
    public static Cache<String, ?> cache = new RedisDistributedCache<>(CacheFactory.REDIS.instance(), CacheFactory.STRING_REDIS.instance());


    /**
     * 全局 id 生成器
     */
    private static IdGenerator<?> idGenerator = SnowflakeIdGenerator.INSTANCE;

    /**
     * 获取全局 id 生成器
     */
    @SuppressWarnings("unchecked")
    public static<T> IdGenerator<T> idGenerator () {
        return (IdGenerator<T>) idGenerator;
    }

    /**
     * 设置全局 id 生成器
     */
    public static<T> void setIdGenerator (IdGenerator<T> newIdGenerator) {
        idGenerator = newIdGenerator;
    }


    /**
     * @Author fzx
     * @Description 发布IM事件
     * @param event IMEvent事件的子类
     * @param async 是否异步发布事件 true-异步，false-同步
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
        publishEvent(event, false);
    }





    /**
     * 好友配置的映射缓存
     */
    public static Cache<String, FriendEntity> friendEntityCache = new CaffeineLocalCache<>("friendEntity", Caffeine.newBuilder()
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
                    long baseNanos = TimeUnit.MINUTES.toNanos(NumberConstant.NUMBER_5);
                    // 随机±30秒偏移
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_30, NumberConstant.NUMBER_31));
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
    public static Cache<String, GroupEntity> groupEntityCache = new CaffeineLocalCache<>("groupEntity", Caffeine.newBuilder()
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
                    long baseNanos = TimeUnit.MINUTES.toNanos(NumberConstant.NUMBER_10);
                    // 随机±30秒偏移
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_30, NumberConstant.NUMBER_31));
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
    public static Cache<String, GroupUserEntity> groupUserEntityCache = new CaffeineLocalCache<>("groupUserEntity", Caffeine.newBuilder()
            // 最大条目数：100万（预留20%冗余，避免频繁淘汰）
            .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
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
                    long baseNanos = TimeUnit.MINUTES.toNanos(NumberConstant.NUMBER_10);
                    // 随机±30秒偏移
                    long randomNanos = TimeUnit.SECONDS.toNanos(ThreadLocalRandom.current().nextLong(NumberConstant.NUMBER_NEGATIVE_30, NumberConstant.NUMBER_31));
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
    public static Cache<String, UserEntity> userEntityCache = new CaffeineLocalCache<>("userEntity", Caffeine.newBuilder()
            // 最大条目数：100万（预留20%冗余，避免频繁淘汰）
            .maximumSize(MessageConstant.LOCAL_CACHE_MAX_SIZE)
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

}
