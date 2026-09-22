package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.base.model.RequestSession;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.relation.RelationCacheInvalidatePublisher;
import com.ouyunc.core.relation.RelationLocalCache;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.MongoFriendEntity;
import com.ouyunc.domain.entity.UserEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
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
            byte[] keyBytes = session.serializeOrThrow(infra.stringSerializer, friendRequestCacheKey, "friendRequestCacheKey");
            byte[] valueBytes = session.serializeOrThrow(infra.valueSerializer, requestSession, "requestSession");
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
    }

    public RequestSession getFriendRequestSession(String appKey, String from, String to) {
        Object raw = infra.redisTemplate.opsForValue().get(CacheConstant.buildFriendRequestCacheKey(appKey, from, to));
        return raw instanceof RequestSession requestSession ? requestSession : null;
    }

    /** 清除好友请求会话占位，允许删友后再申请。 */
    public void deleteFriendRequestSession(String appKey, String from, String to) {
        if (appKey == null || from == null || to == null) {
            return;
        }
        infra.redisTemplate.delete(CacheConstant.buildFriendRequestCacheKey(appKey, from, to));
        infra.redisTemplate.delete(CacheConstant.buildFriendRequestCacheKey(appKey, to, from));
    }

    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getTo(), message.getFrom());
            byte[] keyBytes = session.serializeOrThrow(infra.stringSerializer, friendRequestCacheKey, "friendRequestCacheKey");
            byte[] valueBytes = session.serializeOrThrow(infra.valueSerializer, requestSession, "requestSession");
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getFrom(), message.getTo());
            byte[] keyBytes = session.serializeOrThrow(infra.stringSerializer, friendRequestCacheKey, "friendRequestCacheKey");
            byte[] valueBytes = session.serializeOrThrow(infra.valueSerializer, requestSession, "requestSession");
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection) -> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getTo(), message.getFrom());
            byte[] keyBytes = session.serializeOrThrow(infra.stringSerializer, friendRequestCacheKey, "friendRequestCacheKey");
            byte[] valueBytes = session.serializeOrThrow(infra.valueSerializer, requestSession, "requestSession");
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    @SuppressWarnings("unchecked")
    public boolean isFriend(String appKey, String from, String to) {
        // Redis 只缓存「是好友」；未命中回源一对 MySQL，禁止把 ZCARD/_i 当完整名单。
        Boolean cached = RelationLocalCache.FRIEND.get(RelationLocalCache.friendKey(appKey, from, to));
        if (cached != null) {
            return cached;
        }
        String zsetKey = CacheConstant.buildFriendsCacheKey(appKey, from);
        try {
            Double score = infra.stringRedisTemplate.opsForZSet().score(zsetKey, to);
            if (score != null) {
                RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, from, to), true);
                return true;
            }
        } catch (Exception e) {
            log.error("Redis 查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
        }
        if (hasFriendRosterInit(appKey, from)) {
            RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, from, to), false);
            return false;
        }
        Boolean dbFriend = loadFriendExistsFromDb(appKey, from, to);
        if (dbFriend == null) {
            return false;
        }
        if (Boolean.TRUE.equals(dbFriend)) {
            cacheFriendPositive(appKey, from, to);
        }
        RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, from, to), dbFriend);
        return dbFriend;
    }

    /**
     * @return true 存在，false 不存在，null 查询异常（不得缓存 false）
     */
    private Boolean loadFriendExistsFromDb(String appKey, String from, String to) {
        try {
            FriendEntity friendEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
                    .param(FriendEntity.Fields.userId, from)
                    .param(FriendEntity.Fields.friendUserId, to)
                    .param(UserEntity.Fields.appKey, appKey)
                    .query(FriendEntity.class)
                    .optional()
                    .orElse(null);
            return friendEntity != null;
        } catch (Exception e) {
            log.error("从MySQL查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
            return null;
        }
    }

    /**
     * 只把确认存在的好友写入 ZSET，不写一致性标记（残缺名单不能标成与库一致）。
     */
    private void cacheFriendPositive(String appKey, String ownerId, String friendId) {
        if (appKey == null || ownerId == null || friendId == null || friendId.isBlank()
                || CacheConstant.FRIEND_ZSET_INIT_MEMBER.equals(friendId)) {
            return;
        }
        try {
            infra.stringRedisTemplate.opsForZSet().add(
                    CacheConstant.buildFriendsCacheKey(appKey, ownerId),
                    friendId,
                    TimeUtil.currentTimeMillis());
        } catch (Exception e) {
            log.warn("回写好友正缓存失败 appKey={} ownerId={} friendId={}", appKey, ownerId, friendId, e);
        }
    }

    /**
     * 登录/登出通知：一致性标记存在则只读 Redis；缺失则灌库后再读，避免漏通知/多通知。
     */
    public Collection<String> getFriendIds(String appKey, String from) {
        ensureFriendRoster(appKey, from);
        Collection<String> ids = infra.stringRedisTemplate.opsForZSet().range(
                CacheConstant.buildFriendsCacheKey(appKey, from), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return ids.stream()
                .filter(id -> id != null && !CacheConstant.FRIEND_ZSET_INIT_MEMBER.equals(id))
                .toList();
    }

    /**
     * INIT 缺失时把 MySQL 全量灌进 Redis；有 INIT 后通知名单不再扫库。
     */
    private void ensureFriendRoster(String appKey, String ownerId) {
        if (hasFriendRosterInit(appKey, ownerId)) {
            return;
        }
        List<FriendEntity> dbFriends = loadAllFriendsFromDb(appKey, ownerId);
        if (dbFriends == null) {
            return;
        }
        rebuildFriendRosterRedis(appKey, ownerId, dbFriends);
    }

    private boolean hasFriendRosterInit(String appKey, String ownerId) {
        try {
            return Boolean.TRUE.equals(infra.stringRedisTemplate.hasKey(
                    CacheConstant.buildFriendsInitCacheKey(appKey, ownerId)));
        } catch (Exception e) {
            log.warn("读取好友名单一致性标记失败 appKey={} ownerId={}", appKey, ownerId, e);
            return false;
        }
    }

    private List<FriendEntity> loadAllFriendsFromDb(String appKey, String ownerId) {
        try {
            List<FriendEntity> list = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectAllFriend())
                    .param(FriendEntity.Fields.userId, ownerId)
                    .param(UserEntity.Fields.appKey, appKey)
                    .query(FriendEntity.class)
                    .list();
            if (list == null || list.isEmpty()) {
                return List.of();
            }
            if (list.size() > MessageConstant.FRIEND_ROSTER_FULL_LOAD_LIMIT) {
                log.warn("好友名单回源截断 appKey={} ownerId={} size={}", appKey, ownerId, list.size());
                return list.subList(0, MessageConstant.FRIEND_ROSTER_FULL_LOAD_LIMIT);
            }
            return list;
        } catch (Exception e) {
            log.error("MySQL 查询全部好友失败 appKey={} ownerId={}", appKey, ownerId, e);
            return null;
        }
    }

    private void rebuildFriendRosterRedis(String appKey, String ownerId, List<FriendEntity> friends) {
        String zsetKey = CacheConstant.buildFriendsCacheKey(appKey, ownerId);
        String initKey = CacheConstant.buildFriendsInitCacheKey(appKey, ownerId);
        List<FriendEntity> safe = friends == null ? List.of() : friends;
        List<String> args = new ArrayList<>();
        int count = 0;
        for (FriendEntity row : safe) {
            if (row == null || row.getFriendUserId() == null
                    || CacheConstant.FRIEND_ZSET_INIT_MEMBER.equals(row.getFriendUserId())) {
                continue;
            }
            count++;
        }
        args.add(String.valueOf(count));
        for (FriendEntity row : safe) {
            if (row == null || row.getFriendUserId() == null
                    || CacheConstant.FRIEND_ZSET_INIT_MEMBER.equals(row.getFriendUserId())) {
                continue;
            }
            long score = row.getJoinTime() == null ? 0L : row.getJoinTime();
            args.add(String.valueOf(score));
            args.add(row.getFriendUserId());
        }
        try {
            DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                    LuaScriptEnum.USER_GROUPS_REBUILD_SCRIPT.getScript(), Long.class);
            infra.stringRedisTemplate.execute(script, List.of(zsetKey, initKey), args.toArray());
        } catch (Exception e) {
            log.warn("好友名单回源失败 appKey={} ownerId={}", appKey, ownerId, e);
        }
    }

    @SuppressWarnings("unchecked")
    public Mono<FriendEntity> getFriendReactive(String appKey, String from, String to) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);

        // 1. 本地缓存
        FriendEntity localCached = MessageContext.friendEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }

        // 2. Redis缓存（响应式）：opsForValue().get 为 Object，命中只填 L1
        return infra.reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(raw -> {
                    if (!(raw instanceof FriendEntity friendEntity)) {
                        return Mono.empty();
                    }
                    fillLocalFriendCache(cacheKey, friendEntity);
                    return Mono.just(friendEntity);
                })
                .switchIfEmpty(
                        // 3. MySQL 权威
                        Mono.fromCallable(() -> {
                                    try {
                                        return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
                                                .param(FriendEntity.Fields.userId, from)
                                                .param(FriendEntity.Fields.friendUserId, to)
                                                .param(UserEntity.Fields.appKey, appKey)
                                                .query(FriendEntity.class)
                                                .optional()
                                                .orElse(null);
                                    } catch (Exception e) {
                                        log.error("从MySQL查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
                                        return null;
                                    }
                                })
                                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                                .flatMap(friendEntity -> {
                                    if (friendEntity == null) {
                                        return Mono.empty();
                                    }
                                    updateFriendCache(cacheKey, friendEntity);
                                    return Mono.just(friendEntity);
                                })
                                .switchIfEmpty(
                                        // 4. Mongo 兜底
                                        infra.reactiveMongoTemplate.findOne(
                                                        Query.query(Criteria.where(MongoFriendEntity.Fields.userId).is(Long.parseLong(from))
                                                                .and(MongoFriendEntity.Fields.friendUserId).is(Long.parseLong(to))),
                                                        MongoFriendEntity.class)
                                                .map(this::convertMongoFriendToFriend)
                                                .doOnNext(friendEntity -> updateFriendCache(cacheKey, friendEntity))
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
        Object redisValue = infra.redisTemplate.opsForValue().get(cacheKey);
        if (redisValue instanceof FriendEntity redisCached) {
            fillLocalFriendCache(cacheKey, redisCached);
            return redisCached;
        }
        // MySQL 为 shield 等权限权威源
        try {
            FriendEntity friendEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
                    .param(FriendEntity.Fields.userId, ownerUserId)
                    .param(FriendEntity.Fields.friendUserId, friendUserId)
                    .param(UserEntity.Fields.appKey, appKey)
                    .query(FriendEntity.class)
                    .optional()
                    .orElse(null);
            if (friendEntity != null) {
                updateFriendCache(cacheKey, friendEntity);
                return friendEntity;
            }
        } catch (Exception e) {
            log.error("MySQL 查询好友失败, owner={}, friend={}", ownerUserId, friendUserId, e);
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
        return null;
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
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer, (ops, msg, ak, f, t) -> {
        });
    }

    <K, V> boolean bindFriend(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String from = message.getFrom();
        String to = message.getTo();
        String appKey = metadata.getAppKey();
        boolean bound = session.saveMessageWithSession(packet, expireTime, CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer,
                (redisConnection, msg, ak, f, t) -> {
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, from)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(t));
                    redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(f));
                });
        if (bound) {
            RelationLocalCache.markFriend(appKey, from, to, true);
            RelationCacheInvalidatePublisher.publish(
                    RelationCacheInvalidateEvent.friendAdd(appKey, from, to));
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
            fillLocalFriendCache(cacheKey, friendEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, friendEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    /** L2 命中只填本地，禁止读路径续期 Redis。 */
    private void fillLocalFriendCache(String cacheKey, FriendEntity friendEntity) {
        if (friendEntity != null) {
            MessageContext.friendEntityCache.put(cacheKey, friendEntity);
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
        // 好友存在性只看布尔 L1；实体缓存仅补 shield 配置，不反推 isFriend
        Boolean friendHit = RelationLocalCache.FRIEND.get(RelationLocalCache.friendKey(appKey, to, from));
        Boolean blackHit = RelationLocalCache.BLACKLIST.get(RelationLocalCache.blacklistKey(appKey, to, from));
        FriendEntity toFromEntity = MessageContext.friendEntityCache.get(
                CacheConstant.buildFriendsConfigCacheKey(appKey, to, from));
        Boolean shieldHit = RelationLocalCache.SHIELD.get(RelationLocalCache.shieldKey(appKey, to, from));
        if (toFromEntity != null) {
            shieldHit = YesOrNo.YES.getCode().equals(toFromEntity.getShield());
            RelationLocalCache.markShield(appKey, to, from, shieldHit);
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
        byte[] shieldKey = infra.stringSerializer.serialize(CacheConstant.buildFriendsConfigCacheKey(appKey, to, from));
        // closePipeline 拿原始结果，避免 executePipelined 用 valueSerializer 误解码 ZSCORE/HGET
        Object pipelineResult = infra.redisTemplate.execute((RedisCallback<Object>) connection -> {
            connection.openPipeline();
            connection.zSetCommands().zScore(friendsKey, fromBytes);
            connection.hashCommands().hGet(blackKey, fromBytes);
            connection.stringCommands().get(shieldKey);
            return connection.closePipeline();
        });
        @SuppressWarnings("unchecked")
        List<Object> raw = pipelineResult instanceof List<?> list ? (List<Object>) list : List.of();
        boolean friendHit = isScoreHit(raw.size() > 0 ? raw.get(0) : null);
        boolean blacklisted = isBlacklistHit(raw.size() > 1 ? raw.get(1) : null);
        FriendEntity shieldEntity = deserializeFriendEntity(raw.size() > 2 ? raw.get(2) : null);
        boolean shielded = shieldEntity != null && YesOrNo.YES.getCode().equals(shieldEntity.getShield());
        boolean friend = friendHit;
        if (!friendHit) {
            if (hasFriendRosterInit(appKey, to)) {
                friend = false;
                RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, to, from), false);
            } else {
                Boolean dbFriend = loadFriendExistsFromDb(appKey, to, from);
                if (dbFriend != null) {
                    friend = dbFriend;
                    if (friend) {
                        cacheFriendPositive(appKey, to, from);
                    }
                    RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, to, from), friend);
                }
            }
        } else {
            RelationLocalCache.FRIEND.put(RelationLocalCache.friendKey(appKey, to, from), true);
        }
        RelationLocalCache.markBlacklist(appKey, to, from, blacklisted);
        RelationLocalCache.markShield(appKey, to, from, shielded);
        if (shieldEntity != null) {
            MessageContext.friendEntityCache.put(CacheConstant.buildFriendsConfigCacheKey(appKey, to, from), shieldEntity);
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
