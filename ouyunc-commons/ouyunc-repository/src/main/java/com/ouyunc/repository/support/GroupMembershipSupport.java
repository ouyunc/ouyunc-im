package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.constant.enums.GroupUserPost;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.MongoGroupEntity;
import com.ouyunc.domain.entity.MongoGroupUserEntity;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * 群组成员与群组实体查询、绑定。
 */
public final class GroupMembershipSupport {

    private static final Logger log = LoggerFactory.getLogger(GroupMembershipSupport.class);

    private final RepositoryInfrastructure infra;
    private final SessionMessagePersistenceSupport session;

    public GroupMembershipSupport(RepositoryInfrastructure infra, SessionMessagePersistenceSupport session) {
        this.infra = infra;
        this.session = session;
    }

    @SuppressWarnings("unchecked")
    public Set<String> groupUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // score 存储的是用户加入群的时间戳，毫秒
        return infra.stringRedisTemplate.opsForZSet().range(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), message.getTo()), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }

    public GroupUserEntity groupUserEntity(String appKey, String groupId, String memberId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);

        // 1. 本地缓存
        GroupUserEntity groupUserEntity = MessageContext.groupUserEntityCache.get(cacheKey);
        if (groupUserEntity != null) {
            return groupUserEntity;
        }

        // 2. Redis缓存
        groupUserEntity = (GroupUserEntity) infra.redisTemplate.opsForValue().get(cacheKey);
        if (groupUserEntity != null) {
            updateGroupUserCache(cacheKey, groupUserEntity);
            return groupUserEntity;
        }

        // 3. MongoDB
        try {
            MongoGroupUserEntity mongoGroupUser = infra.mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                            .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId))),
                    MongoGroupUserEntity.class);
            if (mongoGroupUser != null) {
                groupUserEntity = convertMongoGroupUserToGroupUser(mongoGroupUser);
                updateGroupUserCache(cacheKey, groupUserEntity);
                return groupUserEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
        }

        // 4. MySQL
        return queryGroupUserEntityFromDataBase(cacheKey, appKey, groupId, memberId);
    }

    GroupUserEntity queryGroupUserEntityFromDataBase(String cacheKey, String appKey, String groupId, String memberId) {
        try {
            GroupUserEntity groupUserEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                    .param(GroupUserEntity.Fields.userId, memberId)
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .query(GroupUserEntity.class)
                    .optional()
                    .orElse(null);
            if (groupUserEntity != null) {
                updateGroupUserCache(cacheKey, groupUserEntity);
            }
            return groupUserEntity;
        } catch (Exception e) {
            log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Mono<GroupUserEntity> groupUserEntityReactive(String appKey, String groupId, String memberId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);

        // 1. 本地缓存
        GroupUserEntity localCached = MessageContext.groupUserEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }

        // 2. Redis缓存（响应式）
        return infra.reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(GroupUserEntity.class)
                .doOnNext((Object groupUserEntity) -> {
                    if (groupUserEntity != null) {
                        updateGroupUserCache(cacheKey, (GroupUserEntity) groupUserEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        infra.reactiveMongoTemplate.findOne(
                                        Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                                                .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId))),
                                        MongoGroupUserEntity.class)
                                .map(this::convertMongoGroupUserToGroupUser)
                                .doOnNext(groupUserEntity -> updateGroupUserCache(cacheKey, groupUserEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        Mono.fromCallable(() -> {
                                                    try {
                                                        return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                                                                .param(GroupUserEntity.Fields.userId, memberId)
                                                                .param(GroupUserEntity.Fields.groupId, groupId)
                                                                .query(GroupUserEntity.class)
                                                                .optional()
                                                                .orElse(null);
                                                    } catch (Exception e) {
                                                        log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                                                        return null;
                                                    }
                                                })
                                                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                                                .doOnNext(groupUserEntity -> {
                                                    if (groupUserEntity != null) {
                                                        updateGroupUserCache(cacheKey, groupUserEntity);
                                                    }
                                                })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                    return Mono.empty();
                });
    }

    @SuppressWarnings("unchecked")
    public Set<String> groupManagerAndLeaderUsersIdentity(Packet packet) {
        return infra.stringRedisTemplate.opsForZSet().rangeByScore(CacheConstant.buildGroupUserCacheKey(packet.getMessage().getMetadata().getAppKey(), packet.getMessage().getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost(Packet packet) {
        Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost = new HashMap<>();
        Set<ZSetOperations.TypedTuple<String>> tuples = infra.stringRedisTemplate.opsForZSet().rangeByScoreWithScores(CacheConstant.buildGroupUserCacheKey(packet.getMessage().getMetadata().getAppKey(), packet.getMessage().getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
        if (tuples != null && !tuples.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                groupManagerAndLeaderUsersIdentityAndPost.put(tuple.getValue(), tuple.getScore());
            }
        }
        return groupManagerAndLeaderUsersIdentityAndPost;
    }

    @SuppressWarnings("unchecked")
    public boolean inGroup(String appKey, String from, String groupId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, from, groupId);
        // 1. 本地缓存
        GroupUserEntity groupUserEntity = MessageContext.groupUserEntityCache.get(cacheKey);
        if (groupUserEntity != null) {
            return true;
        }
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return infra.stringRedisTemplate.opsForZSet().score(CacheConstant.buildGroupUserCacheKey(appKey, groupId), from) != null;
    }

    public GroupEntity getGroupEntity(String appKey, String groupId) {
        String cacheKey = CacheConstant.buildGroupCacheKey(appKey, groupId);

        // 1. 本地缓存
        GroupEntity groupEntity = MessageContext.groupEntityCache.get(cacheKey);
        if (groupEntity != null) {
            return groupEntity;
        }

        // 2. Redis缓存
        groupEntity = (GroupEntity) infra.redisTemplate.opsForValue().get(cacheKey);
        if (groupEntity != null) {
            updateGroupCache(cacheKey, groupEntity);
            return groupEntity;
        }

        // 3. MongoDB
        try {
            MongoGroupEntity mongoGroup = infra.mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoGroupEntity.Fields.id).is(Long.parseLong(groupId))
                            .and(MongoGroupEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                    MongoGroupEntity.class);
            if (mongoGroup != null) {
                groupEntity = convertMongoGroupToGroup(mongoGroup);
                updateGroupCache(cacheKey, groupEntity);
                return groupEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
        }

        // 4. MySQL
        groupEntity = getGroupEntityFromDatabases(appKey, groupId);
        if (groupEntity != null) {
            updateGroupCache(cacheKey, groupEntity);
        }

        return groupEntity;
    }

    @SuppressWarnings("unchecked")
    public Mono<GroupEntity> getGroupEntityReactive(String appKey, String groupId) {
        String cacheKey = CacheConstant.buildGroupCacheKey(appKey, groupId);

        // 1. 本地缓存
        GroupEntity localCached = MessageContext.groupEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }

        // 2. Redis缓存（响应式）
        return infra.reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(GroupEntity.class)
                .doOnNext((Object groupEntity) -> {
                    if (groupEntity != null) {
                        updateGroupCache(cacheKey, (GroupEntity) groupEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        infra.reactiveMongoTemplate.findOne(
                                        Query.query(Criteria.where(MongoGroupEntity.Fields.id).is(Long.parseLong(groupId))
                                                .and(MongoGroupEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                                        MongoGroupEntity.class)
                                .map(this::convertMongoGroupToGroup)
                                .doOnNext(groupEntity -> updateGroupCache(cacheKey, groupEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        getGroupEntityFromDatabasesReactive(appKey, groupId)
                                                .doOnNext(groupEntity -> {
                                                    if (groupEntity != null) {
                                                        updateGroupCache(cacheKey, groupEntity);
                                                    }
                                                })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
                    return Mono.empty();
                });
    }

    public GroupEntity getGroupEntityFromDatabases(String appKey, String groupId) {
        try {
            GroupEntity groupEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroup())
                    .param(GroupEntity.Fields.id, groupId)
                    .query(GroupEntity.class)
                    .single();
            // 走不到这里就会进异常
            infra.redisTemplate.opsForValue().set(CacheConstant.buildGroupCacheKey(appKey, groupId), groupEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            return groupEntity;
        } catch (EmptyResultDataAccessException e) {
            log.warn("群组不存在, groupId: {}", groupId);
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("同一个groupId存在多个群组, groupId: {}", groupId);
            throw new RuntimeException("同一个groupIdy存在多个用于, groupId: " + groupId);
        } catch (Exception e) {
            log.error("获取群组实体异常, groupId: {}, 原因：{}", groupId, e.getMessage());
            throw new RuntimeException("获取群组实体异常, groupId: " + groupId);
        }
    }

    public Mono<GroupEntity> getGroupEntityFromDatabasesReactive(String appKey, String groupId) {
        // 1. 入参校验（提前拦截无效请求，避免线程池资源浪费）
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(groupId)) {
            log.warn("响应式查询群组：appKey 或 groupId 为空，appKey:{}, groupId:{}", appKey, groupId);
            return Mono.empty(); // 空参数返回空流
        }

        // 2. 将同步方法封装为 Supplier（供给型函数，无参有返回值）
        // 注意：Supplier 中的逻辑会在 publishOn 指定的线程池中执行
        return Mono.fromSupplier(() -> getGroupEntityFromDatabases(appKey, groupId))
                // 3. 切换到专用线程池执行同步任务（关键：避免阻塞 Reactor 核心线程）
                .publishOn(Schedulers.fromExecutor(infra.dbExecutor()))
                // 4. 响应式异常处理：将同步方法抛出的 RuntimeException 转换为响应式错误信号
                .onErrorResume(e -> {
                    log.error("响应式查询群组异常, appKey:{}, groupId:{}", appKey, groupId, e);
                    // 返回错误信号，上游可通过 onError 捕获
                    return Mono.error(new RuntimeException("响应式查询群组失败, groupId: " + groupId, e));
                })
                // 5. 日志记录：打印响应式流的结果（可选，用于调试）
                .doOnSuccess(groupEntity -> {
                    if (groupEntity == null) {
                        log.debug("响应式查询群组：未找到群组, appKey:{}, groupId:{}", appKey, groupId);
                    } else {
                        log.debug("响应式查询群组：成功获取群组, appKey:{}, groupId:{}, 状态:{}",
                                appKey, groupId, groupEntity.getStatus());
                    }
                });
    }

    public GroupRequestSession getGroupRequestSession(String appKey, String joiner, String groupId) {
        return (GroupRequestSession) infra.redisTemplate.opsForValue().get(CacheConstant.buildGroupRequestCacheKey(appKey, joiner, groupId));
    }

    public boolean autoPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean manualPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean saveJoinGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
    }

    public boolean saveGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public <K, V> boolean saveGroupRequestMessage(Packet packet, String groupId, String requestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), consumer, (ops, msg, ak, f, t) -> {
        });
    }

    @SuppressWarnings("unchecked")
    public<K, V> boolean bindGroup(Packet packet, String joiner, String groupId, String requestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), consumer, (redisConnection, msg, ak, f, t) -> {
            // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
            // 建立双向好友关系（仅bindFriend方法需要的逻辑）
            // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
            // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
            // 转换键和值为字符串类型的键
            redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), groupId)), GroupUserPost.ORDINARY.value(), infra.stringSerializer.serialize(joiner));
            redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildUserGroupsCacheKey(metadata.getAppKey(), joiner)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(groupId));
        });
    }

    public void updateGroupUserCache(String cacheKey, GroupUserEntity groupUserEntity) {
        if (groupUserEntity != null) {
            MessageContext.groupUserEntityCache.put(cacheKey, groupUserEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, groupUserEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    public GroupUserEntity convertMongoGroupUserToGroupUser(MongoGroupUserEntity mongoGroupUser) {
        if (mongoGroupUser == null) {
            return null;
        }
        GroupUserEntity groupUserEntity = new GroupUserEntity();
        groupUserEntity.setId(mongoGroupUser.getId());
        groupUserEntity.setGroupId(mongoGroupUser.getGroupId());
        groupUserEntity.setGroupCode(mongoGroupUser.getGroupCode());
        groupUserEntity.setGroupNickName(mongoGroupUser.getGroupNickName());
        groupUserEntity.setUserId(mongoGroupUser.getUserId());
        groupUserEntity.setUserCode(mongoGroupUser.getUserCode());
        groupUserEntity.setPost(mongoGroupUser.getPost());
        groupUserEntity.setSilence(mongoGroupUser.getSilence());
        groupUserEntity.setUserNickName(mongoGroupUser.getUserNickName());
        groupUserEntity.setShield(mongoGroupUser.getShield());
        groupUserEntity.setWay(mongoGroupUser.getWay());
        groupUserEntity.setChannel(mongoGroupUser.getChannel());
        groupUserEntity.setJoinTime(mongoGroupUser.getJoinTime());
        groupUserEntity.setCreateTime(mongoGroupUser.getCreateTime());
        return groupUserEntity;
    }

    public void updateGroupCache(String cacheKey, GroupEntity groupEntity) {
        if (groupEntity != null) {
            MessageContext.groupEntityCache.put(cacheKey, groupEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, groupEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    public GroupEntity convertMongoGroupToGroup(MongoGroupEntity mongoGroup) {
        if (mongoGroup == null) {
            return null;
        }
        GroupEntity groupEntity = new GroupEntity();
        groupEntity.setId(mongoGroup.getId());
        groupEntity.setGroupCode(mongoGroup.getGroupCode());
        groupEntity.setGroupName(mongoGroup.getGroupName());
        groupEntity.setGroupAvatar(mongoGroup.getGroupAvatar());
        groupEntity.setGroupDescription(mongoGroup.getGroupDescription());
        groupEntity.setGroupAnnouncement(mongoGroup.getGroupAnnouncement());
        groupEntity.setGroupJoinPolicy(mongoGroup.getGroupJoinPolicy());
        groupEntity.setStatus(mongoGroup.getStatus());
        groupEntity.setSilence(mongoGroup.getSilence());
        groupEntity.setAppKey(mongoGroup.getAppKey());
        groupEntity.setCreateTime(mongoGroup.getCreateTime());
        groupEntity.setUpdateTime(mongoGroup.getUpdateTime());
        groupEntity.setDeleted(mongoGroup.getDeleted());
        return groupEntity;
    }
}
