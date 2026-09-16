package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.relation.RelationLocalCache;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.constant.enums.GroupUserPost;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
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
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
        String appKey = metadata.getAppKey();
        String groupId = message.getTo();
        String cacheKey = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        Set<String> cached = MessageContext.groupUserIdentityCache.get(cacheKey);
        if (cached != null) {
            // 调用方会 remove 发送者，必须返回可变副本；缓存内为不可变快照
            return new HashSet<>(cached);
        }
        Set<String> fromRedis = loadGroupUserIdsByScan(cacheKey);
        if (fromRedis != null && !fromRedis.isEmpty()) {
            Set<String> snapshot = Set.copyOf(fromRedis);
            MessageContext.groupUserIdentityCache.put(cacheKey, snapshot);
            return new HashSet<>(snapshot);
        }
        String versionBefore = currentRelationVersion(appKey, groupId);
        List<GroupUserEntity> dbMembers;
        try {
            dbMembers = loadAllGroupUsersFromAuthority(appKey, groupId);
        } catch (GroupMembershipLoadException e) {
            log.error("群成员权威回源失败，拒绝写入空缓存 groupId={}", groupId, e);
            return new HashSet<>();
        }
        if (dbMembers.isEmpty()) {
            // Redis miss 且库中确认无成员：禁止把空集写入 Caffeine，避免误当成「群已空」
            return new HashSet<>();
        }
        if (!rebuildGroupMemberRedis(appKey, groupId, dbMembers, versionBefore)) {
            // 版本已变：不覆盖；尽量读回并发写入后的 Redis
            Set<String> after = loadGroupUserIdsByScan(cacheKey);
            if (after != null && !after.isEmpty()) {
                MessageContext.groupUserIdentityCache.put(cacheKey, Set.copyOf(after));
                return new HashSet<>(after);
            }
        }
        Set<String> ids = new HashSet<>();
        for (GroupUserEntity member : dbMembers) {
            if (member.getUserId() != null) {
                ids.add(member.getUserId());
            }
        }
        MessageContext.groupUserIdentityCache.put(cacheKey, Set.copyOf(ids));
        return ids;
    }

    /**
     * 大群成员用 ZSCAN 分段拉取，避免一次 ZRANGE 0 -1 堵 Redis/堆。
     */
    private Set<String> loadGroupUserIdsByScan(String cacheKey) {
        Set<String> ids = new HashSet<>();
        ScanOptions options = ScanOptions.scanOptions()
                .count(MessageConstant.GROUP_MEMBER_ZSET_SCAN_COUNT)
                .build();
        try (Cursor<ZSetOperations.TypedTuple<String>> cursor =
                     infra.stringRedisTemplate.opsForZSet().scan(cacheKey, options)) {
            if (cursor == null) {
                return ids;
            }
            while (cursor.hasNext()) {
                ZSetOperations.TypedTuple<String> tuple = cursor.next();
                if (tuple != null && tuple.getValue() != null) {
                    ids.add(tuple.getValue());
                }
            }
        } catch (Exception e) {
            log.error("群成员 ZSCAN 失败 cacheKey={}", cacheKey, e);
            return Set.of();
        }
        return ids;
    }

    /**
     * 过滤已屏蔽本群消息的成员。优先读群级屏蔽 Hash；索引未建时回源后重建，避免大群 N 次 GET。
     * 回源失败时 fail-closed：视为全部未屏蔽过滤失败，返回原集合（不投递风险由上层处理），绝不写入「已初始化无屏蔽」。
     */
    public Set<String> excludeGroupShieldedMembers(String appKey, String groupId, Set<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        String shieldKey = CacheConstant.buildGroupShieldCacheKey(appKey, groupId);
        Map<Object, Object> shieldHash = infra.stringRedisTemplate.opsForHash().entries(shieldKey);
        if (shieldHash == null || !shieldHash.containsKey(MessageConstant.GROUP_SHIELD_HASH_INIT_FIELD)) {
            String versionBefore = currentRelationVersion(appKey, groupId);
            List<GroupUserEntity> dbMembers;
            try {
                dbMembers = loadAllGroupUsersFromAuthority(appKey, groupId);
            } catch (GroupMembershipLoadException e) {
                log.error("屏蔽索引回源失败，不写空初始化 groupId={}", groupId, e);
                return new HashSet<>(memberIds);
            }
            if (!writeShieldHashIfVersionMatch(appKey, groupId, dbMembers, versionBefore)) {
                log.warn("屏蔽索引回源版本冲突，跳过覆盖 groupId={}", groupId);
                return new HashSet<>(memberIds);
            }
            shieldHash = infra.stringRedisTemplate.opsForHash().entries(shieldKey);
        }
        if (shieldHash == null || !shieldHash.containsKey(MessageConstant.GROUP_SHIELD_HASH_INIT_FIELD)) {
            return new HashSet<>(memberIds);
        }
        Set<String> result = new HashSet<>();
        for (String memberId : memberIds) {
            if (memberId != null && !shieldHash.containsKey(memberId)) {
                result.add(memberId);
            }
        }
        return result;
    }

    public long groupMemberCount(String appKey, String groupId) {
        Long zcard = infra.stringRedisTemplate.opsForZSet().zCard(
                CacheConstant.buildGroupUserCacheKey(appKey, groupId));
        if (zcard != null && zcard > 0) {
            return zcard;
        }
        return countFromDb(JdbcSqlDialectHolder.countGroupUsersByGroup(),
                GroupUserEntity.Fields.groupId, groupId, appKey);
    }

    public long userGroupCount(String appKey, String userId) {
        Long zcard = infra.stringRedisTemplate.opsForZSet().zCard(
                CacheConstant.buildUserGroupsCacheKey(appKey, userId));
        if (zcard != null && zcard > 0) {
            return zcard;
        }
        return countFromDb(JdbcSqlDialectHolder.countGroupsByUser(),
                GroupUserEntity.Fields.userId, userId, appKey);
    }

    private long countFromDb(String sql, String paramName, String paramValue, String appKey) {
        try {
            Long count = infra.jdbcClient.sql(sql)
                    .param(paramName, paramValue)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(Long.class)
                    .optional()
                    .orElse(0L);
            return count == null ? 0L : count;
        } catch (Exception e) {
            log.error("统计群/成员数量失败 param={} value={} appKey={}", paramName, paramValue, appKey, e);
            return 0L;
        }
    }

    /**
     * 关系权威源：MySQL。Mongo 异步滞后时非空集合不能当作完整真相。
     * 查询失败抛 {@link GroupMembershipLoadException}，不得当成空群。
     */
    private List<GroupUserEntity> loadAllGroupUsersFromAuthority(String appKey, String groupId) {
        try {
            List<GroupUserEntity> mysqlList = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectAllGroupUser())
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(GroupUserEntity.class)
                    .list();
            return mysqlList == null ? List.of() : mysqlList;
        } catch (Exception e) {
            throw new GroupMembershipLoadException("MySQL 查询群成员失败 groupId=" + groupId, e);
        }
    }

    /**
     * @return true 表示按 expectedVersion 重建成功；false 表示版本已变，未覆盖
     */
    private boolean rebuildGroupMemberRedis(String appKey, String groupId, List<GroupUserEntity> members,
                                            String expectedVersion) {
        String zsetKey = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        String versionKey = CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId);
        List<String> args = new ArrayList<>();
        args.add(expectedVersion == null ? "0" : expectedVersion);
        List<GroupUserEntity> safeMembers = members == null ? List.of() : members;
        int count = 0;
        for (GroupUserEntity member : safeMembers) {
            if (member == null || member.getUserId() == null) {
                continue;
            }
            count++;
        }
        args.add(String.valueOf(count));
        for (GroupUserEntity member : safeMembers) {
            if (member == null || member.getUserId() == null) {
                continue;
            }
            double score = member.getPost() == null ? GroupUserPost.ORDINARY.value() : member.getPost();
            args.add(String.valueOf(score));
            args.add(member.getUserId());
        }
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.GROUP_MEMBER_REBUILD_CAS_SCRIPT.getScript(), Long.class);
        Long ok = infra.stringRedisTemplate.execute(script, List.of(zsetKey, versionKey), args.toArray());
        if (ok == null || ok != 1L) {
            log.warn("群成员回源 CAS 未命中 appKey={} groupId={} expectedVersion={}", appKey, groupId, expectedVersion);
            return false;
        }
        return writeShieldHashIfVersionMatch(appKey, groupId, members, expectedVersion);
    }

    private boolean writeShieldHashIfVersionMatch(String appKey, String groupId, List<GroupUserEntity> members,
                                                  String expectedVersion) {
        String versionNow = currentRelationVersion(appKey, groupId);
        if (!Objects.equals(expectedVersion == null ? "0" : expectedVersion, versionNow)) {
            return false;
        }
        writeShieldHash(appKey, groupId, members);
        // 写完后再比对一次，变了则删掉半成品初始化标记，避免错误「无屏蔽」
        String versionAfter = currentRelationVersion(appKey, groupId);
        if (!Objects.equals(expectedVersion == null ? "0" : expectedVersion, versionAfter)) {
            infra.stringRedisTemplate.delete(CacheConstant.buildGroupShieldCacheKey(appKey, groupId));
            return false;
        }
        return true;
    }

    private void writeShieldHash(String appKey, String groupId, List<GroupUserEntity> members) {
        String key = CacheConstant.buildGroupShieldCacheKey(appKey, groupId);
        Map<String, String> fields = new HashMap<>();
        fields.put(MessageConstant.GROUP_SHIELD_HASH_INIT_FIELD, "1");
        if (members != null) {
            for (GroupUserEntity member : members) {
                if (member != null && member.getUserId() != null
                        && YesOrNo.YES.getCode().equals(member.getShield())) {
                    fields.put(member.getUserId(), "1");
                }
            }
        }
        infra.stringRedisTemplate.delete(key);
        infra.stringRedisTemplate.opsForHash().putAll(key, fields);
    }

    private String currentRelationVersion(String appKey, String groupId) {
        String raw = infra.stringRedisTemplate.opsForValue().get(
                CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId));
        return StringUtils.isBlank(raw) ? "0" : raw.trim();
    }

    /** 加群/退群等关系变更后递增，使进行中的旧快照回源失效。 */
    public void bumpGroupRelationVersion(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        infra.stringRedisTemplate.opsForValue().increment(
                CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId));
    }

    private static final class GroupMembershipLoadException extends RuntimeException {
        private GroupMembershipLoadException(String message, Throwable cause) {
            super(message, cause);
        }
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
            // L2 命中只填本地，禁止无条件回写 Redis 续期
            fillLocalGroupUserCache(cacheKey, groupUserEntity);
            return groupUserEntity;
        }

        // 3. MySQL 为禁言/屏蔽等权限权威源；Mongo 滞后不得钉死错误状态
        GroupUserEntity fromMysql = queryGroupUserEntityFromDataBase(cacheKey, appKey, groupId, memberId);
        if (fromMysql != null) {
            return fromMysql;
        }

        // 4. MySQL miss 时再尝试 Mongo（仅兜底，仍写缓存供投递 channel）
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
        return null;
    }

    /**
     * 批量加载群成员配置（优先 L1/L2，miss 一次 JDBC IN 查询）。
     */
    public Map<String, GroupUserEntity> groupUserEntitiesBatch(String appKey, String groupId, Collection<String> memberIds) {
        Map<String, GroupUserEntity> result = new HashMap<>();
        if (memberIds == null || memberIds.isEmpty()) {
            return result;
        }
        List<String> missing = new ArrayList<>();
        for (String memberId : memberIds) {
            if (memberId == null) {
                continue;
            }
            String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);
            GroupUserEntity local = MessageContext.groupUserEntityCache.get(cacheKey);
            if (local != null) {
                result.put(memberId, local);
                continue;
            }
            GroupUserEntity redis = (GroupUserEntity) infra.redisTemplate.opsForValue().get(cacheKey);
            if (redis != null) {
                fillLocalGroupUserCache(cacheKey, redis);
                result.put(memberId, redis);
                continue;
            }
            missing.add(memberId);
        }
        if (missing.isEmpty()) {
            return result;
        }
        try {
            List<GroupUserEntity> rows = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUserBatch())
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param("userIds", missing)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(GroupUserEntity.class)
                    .list();
            if (rows != null) {
                for (GroupUserEntity row : rows) {
                    if (row == null || row.getUserId() == null) {
                        continue;
                    }
                    String mid = String.valueOf(row.getUserId());
                    String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, mid, groupId);
                    updateGroupUserCache(cacheKey, row);
                    result.put(mid, row);
                }
            }
        } catch (Exception e) {
            log.error("批量查询群成员失败 groupId={} missingSize={}", groupId, missing.size(), e);
            for (String mid : missing) {
                GroupUserEntity one = groupUserEntity(appKey, groupId, mid);
                if (one != null) {
                    result.put(mid, one);
                }
            }
        }
        return result;
    }

    GroupUserEntity queryGroupUserEntityFromDataBase(String cacheKey, String appKey, String groupId, String memberId) {
        try {
            GroupUserEntity groupUserEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                    .param(GroupUserEntity.Fields.userId, memberId)
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
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

        // 2. Redis缓存（响应式）：命中只填 L1，L2 写入放到受控执行器且仅回源路径
        return infra.reactiveRedisTemplate.opsForValue().get(cacheKey)
                .flatMap(raw -> {
                    if (!(raw instanceof GroupUserEntity entity)) {
                        return Mono.empty();
                    }
                    fillLocalGroupUserCache(cacheKey, entity);
                    return Mono.just(entity);
                })
                .switchIfEmpty(
                        // 3. MySQL 权威源
                        Mono.fromCallable(() -> {
                                    try {
                                        return infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                                                .param(GroupUserEntity.Fields.userId, memberId)
                                                .param(GroupUserEntity.Fields.groupId, groupId)
                                                .param(GroupEntity.Fields.appKey, appKey)
                                                .query(GroupUserEntity.class)
                                                .optional()
                                                .orElse(null);
                                    } catch (Exception e) {
                                        log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                                        return null;
                                    }
                                })
                                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                                .flatMap(groupUserEntity -> {
                                    if (groupUserEntity == null) {
                                        return Mono.empty();
                                    }
                                    return writeThroughGroupUserCacheAsync(cacheKey, groupUserEntity)
                                            .thenReturn(groupUserEntity);
                                })
                                .switchIfEmpty(
                                        // 4. Mongo 兜底
                                        infra.reactiveMongoTemplate.findOne(
                                                        Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                                                                .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId))),
                                                        MongoGroupUserEntity.class)
                                                .map(this::convertMongoGroupUserToGroupUser)
                                                .flatMap(groupUserEntity -> writeThroughGroupUserCacheAsync(cacheKey, groupUserEntity)
                                                        .thenReturn(groupUserEntity))
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> writeThroughGroupUserCacheAsync(String cacheKey, GroupUserEntity groupUserEntity) {
        return Mono.fromRunnable(() -> updateGroupUserCache(cacheKey, groupUserEntity))
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                .then();
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
        // 存在性只信布尔 L1 + Redis ZSET；identity/实体缓存仅服务扇出与配置，不反推在群
        Boolean cached = RelationLocalCache.GROUP_MEMBER.get(RelationLocalCache.groupMemberKey(appKey, groupId, from));
        if (cached != null) {
            return cached;
        }
        boolean member = infra.stringRedisTemplate.opsForZSet().score(
                CacheConstant.buildGroupUserCacheKey(appKey, groupId), from) != null;
        RelationLocalCache.markGroupMember(appKey, groupId, from, member);
        return member;
    }

    public Mono<Boolean> isGroupMemberReactive(String appKey, String groupId, String memberId) {
        return Mono.fromCallable(() -> inGroup(appKey, memberId, groupId))
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()));
    }

    public GroupEntity getGroupEntity(String appKey, String groupId) {
        String cacheKey = CacheConstant.buildGroupCacheKey(appKey, groupId);

        GroupEntity groupEntity = MessageContext.groupEntityCache.get(cacheKey);
        if (groupEntity != null) {
            if (isLiveGroup(groupEntity)) {
                return groupEntity;
            }
            MessageContext.groupEntityCache.delete(cacheKey);
            return null;
        }

        Object redisValue = infra.redisTemplate.opsForValue().get(cacheKey);
        if (redisValue instanceof GroupEntity redisGroup) {
            if (!isLiveGroup(redisGroup)) {
                return null;
            }
            updateGroupCache(cacheKey, redisGroup);
            return redisGroup;
        }

        try {
            MongoGroupEntity mongoGroup = infra.mongoTemplate.findOne(
                    Query.query(Criteria.where(GroupEntity.Fields.id).is(Long.parseLong(groupId))
                            .and(GroupEntity.Fields.appKey).is(appKey)
                            .and(GroupEntity.Fields.delFlag).is(0L)),
                    MongoGroupEntity.class);
            if (mongoGroup != null) {
                groupEntity = convertMongoGroupToGroup(mongoGroup);
                if (isLiveGroup(groupEntity) && appKey.equals(groupEntity.getAppKey())) {
                    updateGroupCache(cacheKey, groupEntity);
                    return groupEntity;
                }
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
        }

        groupEntity = getGroupEntityFromDatabases(appKey, groupId);
        if (isLiveGroup(groupEntity)) {
            updateGroupCache(cacheKey, groupEntity);
            return groupEntity;
        }
        return null;
    }

    public Mono<GroupEntity> getGroupEntityReactive(String appKey, String groupId) {
        return Mono.fromCallable(() -> getGroupEntity(appKey, groupId))
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                .flatMap(group -> group == null ? Mono.empty() : Mono.just(group))
                .onErrorResume(e -> {
                    log.error("响应式查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
                    return Mono.empty();
                });
    }

    static boolean isLiveGroup(GroupEntity groupEntity) {
        return groupEntity != null && (groupEntity.getDelFlag() == null || groupEntity.getDelFlag() == 0L);
    }

    public GroupEntity getGroupEntityFromDatabases(String appKey, String groupId) {
        try {
            GroupEntity groupEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroup())
                    .param(GroupEntity.Fields.id, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(GroupEntity.class)
                    .single();
            if (groupEntity != null && !appKey.equals(groupEntity.getAppKey())) {
                log.warn("群组租户不匹配, groupId={}, expectAppKey={}, actual={}",
                        groupId, appKey, groupEntity.getAppKey());
                return null;
            }
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

    public void deleteGroupRequestSession(String appKey, String joiner, String groupId) {
        if (appKey == null || joiner == null || groupId == null) {
            return;
        }
        infra.redisTemplate.delete(CacheConstant.buildGroupRequestCacheKey(appKey, joiner, groupId));
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
        boolean bound = session.saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), consumer, (redisConnection, msg, ak, f, t) -> {
            redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), groupId)), GroupUserPost.ORDINARY.value(), infra.stringSerializer.serialize(joiner));
            redisConnection.zSetCommands().zAdd(infra.stringSerializer.serialize(CacheConstant.buildUserGroupsCacheKey(metadata.getAppKey(), joiner)), msg.getMetadata().getServerTime(), infra.stringSerializer.serialize(groupId));
        });
        if (bound) {
            bumpGroupRelationVersion(metadata.getAppKey(), groupId);
            MessageContext.groupUserIdentityCache.delete(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), groupId));
            RelationLocalCache.markGroupMember(metadata.getAppKey(), groupId, joiner, true);
        }
        return bound;
    }

    public void updateGroupUserCache(String cacheKey, GroupUserEntity groupUserEntity) {
        if (groupUserEntity != null) {
            fillLocalGroupUserCache(cacheKey, groupUserEntity);
            infra.redisTemplate.opsForValue().set(cacheKey, groupUserEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    /** 仅填本地缓存，不写 L2（Redis 命中路径） */
    private void fillLocalGroupUserCache(String cacheKey, GroupUserEntity groupUserEntity) {
        if (groupUserEntity != null) {
            MessageContext.groupUserEntityCache.put(cacheKey, groupUserEntity);
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
        if (groupEntity == null) {
            return;
        }
        if (!isLiveGroup(groupEntity)) {
            MessageContext.groupEntityCache.delete(cacheKey);
            return;
        }
        MessageContext.groupEntityCache.put(cacheKey, groupEntity);
        infra.redisTemplate.opsForValue().set(cacheKey, groupEntity,
                MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
        groupEntity.setDelFlag(mongoGroup.getDelFlag());
        return groupEntity;
    }
}
