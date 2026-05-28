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
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.MongoFriendEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Collection;
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
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);
        // 1. 本地缓存
        FriendEntity friendEntity = MessageContext.friendEntityCache.get(cacheKey);
        if (friendEntity != null) {
            return true;
        }
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return infra.stringRedisTemplate.opsForZSet().score(CacheConstant.buildFriendsCacheKey(appKey, from), to) != null;
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
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer,
                (redisConnection, msg, ak, f, t) -> {
                    // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
                    // 建立双向好友关系（仅bindFriend方法需要的逻辑）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, from)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(t));
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(f));
                });
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
        friendEntity.setCreateTime(mongoFriend.getCreateTime());
        friendEntity.setUpdateTime(mongoFriend.getUpdateTime());
        return friendEntity;
    }
}
