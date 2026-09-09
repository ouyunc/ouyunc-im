package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.context.RelationLocalCache;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.MongoFriendEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 好友关系查询与绑定。
 */
public final class FriendRepositorySupport {

    private static final Logger log = LoggerFactory.getLogger(FriendRepositorySupport.class);

    private final RepositoryInfrastructure infra;
    private final SessionMessagePersistenceSupport session;

    public FriendRepositorySupport(RepositoryInfrastructure infra, SessionMessagePersistenceSupport session) {
        this.infra = infra;
        this.session = session;
    }

    public boolean saveJoinFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getFrom(), message.getTo());
            // 修复：key 必须用 stringSerializer，与 saveRefuseFriendRequestMessage 保持一致，否则后续读取时无法命中
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
    }

    public RequestSession getFriendRequestSession(String appKey, String from, String to) {
        return (RequestSession) infra.redisTemplate.opsForValue().get(CacheConstant.buildFriendRequestCacheKey(appKey, from, to));
    }

    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getTo(), message.getFrom());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getFrom(), message.getTo());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getTo(), message.getFrom());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    @SuppressWarnings("unchecked")
    public boolean isFriend(String appKey, String from, String to) {
        Boolean cached = RelationLocalCache.FRIEND.get(RelationLocalCache.friendKey(appKey, from, to));
        if (cached != null) {
            return cached;
        }
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);
        FriendEntity friendEntity = MessageContext.friendEntityCache.get(cacheKey);
        if (friendEntity != null) {
            RelationLocalCache.markFriend(appKey, from, to, true);
            return true;
        }
        boolean friend = infra.stringRedisTemplate.opsForZSet().score(
                CacheConstant.buildFriendsCacheKey(appKey, from), to) != null;
        RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, from, to), friend);
        return friend;
    }

    public Collection<String> getFriendIds(String appKey, String from) {
        return infra.stringRedisTemplate.opsForZSet().range(CacheConstant.buildFriendsCacheKey(appKey, from), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }

    @SuppressWarnings("unchecked")
    public Mono<FriendEntity> getFriendReactive(String appKey, String from, String to) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);

        // 1. 本地缓存
        FriendEntity localCached = MessageContext.friendEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }

        // 2. Redis缓存（响应式）
        return infra.reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(FriendEntity.class)
                .doOnNext((Object friendEntity) -> {
                    if (friendEntity != null) {
                        updateFriendCache(cacheKey, (FriendEntity) friendEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        infra.reactiveMongoTemplate.findOne(
                                        Query.query(Criteria.where(MongoFriendEntity.Fields.userId).is(Long.parseLong(from))
                                                .and(MongoFriendEntity.Fields.friendUserId).is(Long.parseLong(to))),
                                        MongoFriendEntity.class)
                                .map(this::convertMongoFriendToFriend)
                                .doOnNext(friendEntity -> updateFriendCache(cacheKey, friendEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        Mono.fromCallable(() -> {
                                                    try {
                                                        return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
                                                                .param(FriendEntity.Fields.userId, from)
                                                                .param(FriendEntity.Fields.friendUserId, to)
                                                                .query(FriendEntity.class)
                                                                .optional()
                                                                .orElse(null);
                                                    } catch (Exception e) {
                                                        log.error("从MySQL查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
                                                        return null;
                                                    }
                                                })
                                                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                                                .doOnNext(friendEntity -> {
                                                    if (friendEntity != null) {
                                                        updateFriendCache(cacheKey, friendEntity);
                                                    }
                                                })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
                    return Mono.empty();
                });
    }

    /**
     * 同步查询好友配置（含 channel），热路径优先本地/Redis 缓存。
     */
    public FriendEntity getFriendEntity(String appKey, String ownerUserId, String friendUserId) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, ownerUserId, friendUserId);
        FriendEntity localCached = MessageContext.friendEntityCache.get(cacheKey);
        if (localCached != null) {
            return localCached;
        }
        FriendEntity redisCached = (FriendEntity) infra.redisTemplate.opsForValue().get(cacheKey);
        if (redisCached != null) {
            updateFriendCache(cacheKey, redisCached);
            return redisCached;
        }
        try {
            MongoFriendEntity mongoFriend = infra.mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoFriendEntity.Fields.userId).is(parseId(ownerUserId))
                            .and(MongoFriendEntity.Fields.friendUserId).is(parseId(friendUserId))),
                    MongoFriendEntity.class);
            if (mongoFriend != null) {
                FriendEntity friendEntity = convertMongoFriendToFriend(mongoFriend);
                updateFriendCache(cacheKey, friendEntity);
                return friendEntity;
            }
        } catch (Exception e) {
            log.debug("Mongo 查询好友失败, owner={}, friend={}", ownerUserId, friendUserId, e);
        }
        try {
            FriendEntity friendEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
                    .param(FriendEntity.Fields.userId, ownerUserId)
                    .param(FriendEntity.Fields.friendUserId, friendUserId)
                    .query(FriendEntity.class)
                    .optional()
                    .orElse(null);
            if (friendEntity != null) {
                updateFriendCache(cacheKey, friendEntity);
            }
            return friendEntity;
        } catch (Exception e) {
            log.error("MySQL 查询好友失败, owner={}, friend={}", ownerUserId, friendUserId, e);
            return null;
        }
    }

    private static Object parseId(String userId) {
        try {
            return Long.parseLong(userId);
        } catch (NumberFormatException ex) {
            return userId;
        }
    }

    <K, V> boolean saveFriendRequestMessage(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        // 调用公共方法，传入空的额外操作
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String from = message.getFrom();
        String to = message.getTo();
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer, (ops, msg, ak, f, t) -> {
        });
    }

    <K, V> boolean bindFriend(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String from = message.getFrom();
        String to = message.getTo();
        String appKey = metadata.getAppKey();
        boolean bound = session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer,
                (redisConnection, msg, ak, f, t) -> {
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, from)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(t));
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(f));
                });
        if (bound) {
            RelationLocalCache.markFriend(appKey, from, to, true);
        }
        return bound;
    }

    /**
     * 私聊热路径：Caffeine 命中则零 Redis；否则一次 Pipeline 读好友 ZSCORE + 拉黑 HGET + 屏蔽配置 GET。
     */
    public Mono<One2OneChatAccess> loadOne2OneChatAccess(String appKey, String from, String to) {
        One2OneChatAccess local = loadOne2OneChatAccessFromLocal(appKey, from, to);
        if (local != null) {
            return Mono.just(local);
        }
        return Mono.fromCallable(() -> loadOne2OneChatAccessFromRedis(appKey, from, to))
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()));
    }

    void updateFriendCache(String cacheKey, FriendEntity friendEntity) {
        if (friendEntity != null) {
            MessageContext.friendEntityCache.put(cacheKey, friendEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, friendEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    FriendEntity convertMongoFriendToFriend(MongoFriendEntity mongoFriend) {
        if (mongoFriend == null) {
            return null;
        }
        FriendEntity friendEntity = new FriendEntity();
        friendEntity.setId(mongoFriend.getId());
        friendEntity.setUserId(mongoFriend.getUserId());
        friendEntity.setFriendUserId(mongoFriend.getFriendUserId());
        friendEntity.setFriendUserCode(mongoFriend.getFriendUserCode());
        friendEntity.setFriendNickName(mongoFriend.getFriendNickName());
        friendEntity.setShield(mongoFriend.getShield());
        friendEntity.setWay(mongoFriend.getWay());
        friendEntity.setChannel(mongoFriend.getChannel());
        friendEntity.setJoinTime(mongoFriend.getJoinTime());
        friendEntity.setCreateTime(mongoFriend.getCreateTime());
        friendEntity.setUpdateTime(mongoFriend.getUpdateTime());
        return friendEntity;
    }

    private One2OneChatAccess loadOne2OneChatAccessFromLocal(String appKey, String from, String to) {
        Boolean friendHit = RelationLocalCache.FRIEND.get(RelationLocalCache.friendKey(appKey, to, from));
        if (friendHit == null && MessageContext.friendEntityCache.get(
                CacheConstant.buildFriendsConfigCacheKey(appKey, to, from)) != null) {
            friendHit = Boolean.TRUE;
            RelationLocalCache.markFriend(appKey, from, to, true);
        }
        Boolean blackHit = RelationLocalCache.BLACKLIST.get(RelationLocalCache.blacklistKey(appKey, to, from));
        FriendEntity fromToEntity = MessageContext.friendEntityCache.get(
                CacheConstant.buildFriendsConfigCacheKey(appKey, from, to));
        Boolean shieldHit = RelationLocalCache.SHIELD.get(RelationLocalCache.shieldKey(appKey, from, to));
        if (fromToEntity != null) {
            shieldHit = YesOrNo.YES.getCode().equals(fromToEntity.getShield());
            RelationLocalCache.markShield(appKey, from, to, shieldHit);
        }
        // 已知拒绝条件可短路，避免为否决路径再打 Redis
        if (Boolean.FALSE.equals(friendHit)) {
            return new One2OneChatAccess(false, false, false);
        }
        if (Boolean.TRUE.equals(blackHit)) {
            return new One2OneChatAccess(true, true, false);
        }
        if (Boolean.TRUE.equals(shieldHit)) {
            return new One2OneChatAccess(true, false, true);
        }
        if (friendHit == null || blackHit == null || shieldHit == null) {
            return null;
        }
        return new One2OneChatAccess(friendHit, blackHit, shieldHit);
    }

    @SuppressWarnings("unchecked")
    private One2OneChatAccess loadOne2OneChatAccessFromRedis(String appKey, String from, String to) {
        byte[] friendsKey = infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to));
        byte[] fromBytes = infra.stringSerializer.serialize(from);
        byte[] blackKey = infra.stringSerializer.serialize(CacheConstant.buildBlacklistCacheKey(appKey, to));
        byte[] shieldKey = infra.stringSerializer.serialize(CacheConstant.buildFriendsConfigCacheKey(appKey, from, to));
        // closePipeline 拿原始结果，避免 executePipelined 用 valueSerializer 误解码 ZSCORE/HGET
        List<Object> raw = infra.redisTemplate.execute((RedisCallback<List<Object>>) connection -> {
            connection.openPipeline();
            connection.zSetCommands().zScore(friendsKey, fromBytes);
            connection.hashCommands().hGet(blackKey, fromBytes);
            connection.stringCommands().get(shieldKey);
            return connection.closePipeline();
        });
        boolean friend = isScoreHit(raw != null && raw.size() > 0 ? raw.get(0) : null);
        boolean blacklisted = isBlacklistHit(raw != null && raw.size() > 1 ? raw.get(1) : null);
        FriendEntity shieldEntity = deserializeFriendEntity(raw != null && raw.size() > 2 ? raw.get(2) : null);
        boolean shielded = shieldEntity != null && YesOrNo.YES.getCode().equals(shieldEntity.getShield());
        RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, to, from), friend);
        RelationLocalCache.markBlacklist(appKey, to, from, blacklisted);
        RelationLocalCache.markShield(appKey, from, to, shielded);
        if (shieldEntity != null) {
            MessageContext.friendEntityCache.put(CacheConstant.buildFriendsConfigCacheKey(appKey, from, to), shieldEntity);
        }
        return new One2OneChatAccess(friend, blacklisted, shielded);
    }

    private static boolean isScoreHit(Object score) {
        return score != null;
    }

    private static boolean isBlacklistHit(Object hashValue) {
        if (hashValue == null) {
            return false;
        }
        if (hashValue instanceof Number number) {
            return number.longValue() > 0;
        }
        if (hashValue instanceof byte[] bytes) {
            if (bytes.length == 0) {
                return false;
            }
            try {
                return Long.parseLong(new String(bytes, java.nio.charset.StandardCharsets.UTF_8)) > 0;
            } catch (NumberFormatException ignored) {
                return true;
            }
        }
        try {
            return Long.parseLong(hashValue.toString()) > 0;
        } catch (NumberFormatException ignored) {
            return true;
        }
    }

    private FriendEntity deserializeFriendEntity(Object raw) {
        if (raw == null) {
            return null;
        }
        if (raw instanceof FriendEntity entity) {
            return entity;
        }
        if (raw instanceof byte[] bytes) {
            Object decoded = infra.valueSerializer.deserialize(bytes);
            return decoded instanceof FriendEntity entity ? entity : null;
        }
        return null;
    }
}
