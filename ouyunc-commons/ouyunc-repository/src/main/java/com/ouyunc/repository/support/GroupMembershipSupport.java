package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.relation.RelationCacheInvalidatePublisher;
import com.ouyunc.core.relation.RelationLocalCache;
import com.ouyunc.base.model.RelationCacheInvalidateEvent;
import com.ouyunc.base.model.GroupRequestSession;
import com.ouyunc.base.constant.enums.GroupUserPost;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.YesOrNo;
import com.ouyunc.domain.entity.GroupEntity;
import com.ouyunc.domain.entity.GroupUserEntity;
import com.ouyunc.domain.entity.MongoGroupEntity;
import com.ouyunc.domain.entity.MongoGroupUserEntity;
import com.ouyunc.base.constant.enums.BindGroupEnum;
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
import java.util.Collections;
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
    private static final int GROUP_USER_DB_BATCH_SIZE = 500;

    private final RepositoryInfrastructure infra;
    private final SessionMessagePersistenceSupport session;
    private static final Object[] SHIELD_REBUILD_LOCKS = new Object[MessageConstant.RELATION_REBUILD_LOCK_STRIPES];
    private static final Object[] ROSTER_REBUILD_LOCKS = new Object[MessageConstant.RELATION_REBUILD_LOCK_STRIPES];

    static {
        for (int i = 0; i < SHIELD_REBUILD_LOCKS.length; i++) {
            SHIELD_REBUILD_LOCKS[i] = new Object();
            ROSTER_REBUILD_LOCKS[i] = new Object();
        }
    }

    public GroupMembershipSupport(RepositoryInfrastructure infra, SessionMessagePersistenceSupport session) {
        this.infra = infra;
        this.session = session;
    }

    /**
     * 群成员 identity 集合。热路径只读 Redis；INIT 缺失才 MySQL 灌 Redis，不把部分 ZSET 当完整名单。
     * 权威回源失败抛 {@link GroupMembershipLoadException}，不得当成空群。
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getIngress().getAppKey();
        String groupId = message.getTo();
        String cacheKey = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        Set<String> cached = MessageContext.groupUserIdentityCache.get(cacheKey);
        if (cached != null) {
            return cached;
        }
        ensureGroupMemberRoster(appKey, groupId);
        if (!hasGroupMemberInit(appKey, groupId)) {
            throw new GroupMembershipLoadException(
                    "群成员名单未完成权威重建, appKey=" + appKey + ", groupId=" + groupId);
        }
        // 扫描期间可能有入群/退群删掉 INIT 并重建，扫描后必须再核 INIT 与关系版本，否则会缓存残缺名单
        String versionBefore = currentRelationVersion(appKey, groupId);
        Set<String> fromRedis = loadGroupUserIdsByScan(cacheKey);
        if (fromRedis == null) {
            fromRedis = Set.of();
        }
        if (!hasGroupMemberInit(appKey, groupId)
                || !StringUtils.equals(versionBefore, currentRelationVersion(appKey, groupId))) {
            throw new GroupMembershipLoadException(
                    "群成员名单在扫描期间发生变更, appKey=" + appKey + ", groupId=" + groupId);
        }
        return snapshotIdentities(cacheKey, fromRedis);
    }

    /**
     * INIT 缺失时把 MySQL 全量灌进 Redis；有 INIT 后名单/扇出不再扫库。
     */
    private void ensureGroupMemberRoster(String appKey, String groupId) {
        if (RelationRosterRedis.skipRebuild(groupMemberInitState(appKey, groupId))) {
            return;
        }
        Object lock = rosterRebuildLock(appKey, groupId);
        synchronized (lock) {
            if (RelationRosterRedis.skipRebuild(groupMemberInitState(appKey, groupId))) {
                return;
            }
            for (int attempt = 0; attempt < MessageConstant.RELATION_ROSTER_REBUILD_ATTEMPTS; attempt++) {
                if (RelationRosterRedis.skipRebuild(groupMemberInitState(appKey, groupId))) {
                    return;
                }
                String versionBefore = currentRelationVersion(appKey, groupId);
                List<GroupUserEntity> dbMembers = loadAllGroupUsersFromAuthority(appKey, groupId);
                if (rebuildGroupMemberRedis(appKey, groupId, dbMembers, versionBefore)) {
                    return;
                }
            }
        }
    }

    private static Object rosterRebuildLock(String appKey, String groupId) {
        String flightKey = appKey + ":" + groupId;
        return ROSTER_REBUILD_LOCKS[Math.floorMod(flightKey.hashCode(), ROSTER_REBUILD_LOCKS.length)];
    }

    private boolean hasGroupMemberInit(String appKey, String groupId) {
        return RelationRosterRedis.isComplete(groupMemberInitState(appKey, groupId));
    }

    private int groupMemberInitState(String appKey, String groupId) {
        return RelationRosterRedis.checkInit(
                infra.stringRedisTemplate,
                CacheConstant.buildGroupUserCacheKey(appKey, groupId),
                CacheConstant.buildGroupUserInitCacheKey(appKey, groupId));
    }

    private boolean hasUserGroupsInit(String appKey, String userId) {
        return RelationRosterRedis.isComplete(RelationRosterRedis.checkInit(
                infra.stringRedisTemplate,
                CacheConstant.buildUserGroupsCacheKey(appKey, userId),
                CacheConstant.buildUserGroupsInitCacheKey(appKey, userId)));
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
            throw new GroupMembershipLoadException("群成员 ZSCAN 失败, cacheKey=" + cacheKey, e);
        }
        return ids;
    }

    /**
     * 过滤已屏蔽本群消息的成员。
     * <p>索引未就绪必须抛 {@link GroupMembershipLoadException}，不能返回空集。
     * 空集和“全员都已屏蔽”无法区分，调用方会正常返回并 {@code finishDelivery}，
     * 同一 packetId 的重试会看到 done，不再补推。抛错后执行权被释放，客户端重试和本机补投可以继续扇出。
     * 真正无人屏蔽时返回原集合；全员屏蔽时返回空集，那一次完成标记是对的。</p>
     */
    public Set<String> excludeGroupShieldedMembers(String appKey, String groupId, Set<String> memberIds) {
        if (memberIds == null || memberIds.isEmpty()) {
            return Set.of();
        }
        Set<String> shielded;
        try {
            shielded = loadShieldedMembersHot(appKey, groupId, memberIds);
        } catch (GroupMembershipLoadException error) {
            throw error;
        } catch (RuntimeException error) {
            throw new GroupMembershipLoadException("读取群屏蔽索引失败: " + groupId, error);
        }
        if (shielded == null) {
            log.error("群屏蔽索引未就绪，拒绝把空扇出记成完成 appKey={} groupId={}", appKey, groupId);
            throw new GroupMembershipLoadException(
                    "群屏蔽索引未就绪, appKey=" + appKey + ", groupId=" + groupId);
        }
        if (shielded.isEmpty()) {
            return memberIds;
        }
        Set<String> result = new HashSet<>(Math.max(16, memberIds.size() - shielded.size()));
        for (String memberId : memberIds) {
            if (memberId != null && !shielded.contains(memberId)) {
                result.add(memberId);
            }
        }
        return result;
    }

    /**
     * @return 本批已屏蔽成员；索引不可用时抛异常，禁止把未知状态当作无人屏蔽。
     */
    @SuppressWarnings("unchecked")
    private Set<String> loadShieldedMembersHot(String appKey, String groupId, Set<String> memberIds) {
        List<String> members = new ArrayList<>(memberIds);
        Set<String> shielded = new HashSet<>();
        List<String> keys = List.of(CacheConstant.buildGroupShieldCacheKey(appKey, groupId),
                CacheConstant.buildGroupShieldInitCacheKey(appKey, groupId));
        // 只读当前扇出批次；INIT 与字段读取同槽原子完成，避免过期竞态被当作无人屏蔽。
        for (int start = 0; start < members.size(); start += MessageConstant.GROUP_FANOUT_ONLINE_LOOKUP_BATCH) {
            List<String> batch = members.subList(start,
                    Math.min(start + MessageConstant.GROUP_FANOUT_ONLINE_LOOKUP_BATCH, members.size()));
            List<String> flags = infra.stringRedisTemplate.execute(READ_SHIELD_BATCH, keys, batch.toArray());
            if (flags == null || flags.isEmpty()) {
                rebuildShieldIndexSync(appKey, groupId);
                flags = infra.stringRedisTemplate.execute(READ_SHIELD_BATCH, keys, batch.toArray());
            }
            if (flags == null || flags.size() != batch.size()) {
                throw new GroupMembershipLoadException("群屏蔽索引未就绪: " + groupId);
            }
            for (int i = 0; i < batch.size(); i++) {
                if ("1".equals(flags.get(i))) {
                    shielded.add(batch.get(i));
                }
            }
        }
        return shielded;
    }

    /** 字段数由调用方限制；不读取或缓存整群屏蔽名单。 */
    @SuppressWarnings("rawtypes")
    private static final org.springframework.data.redis.core.script.DefaultRedisScript<List> READ_SHIELD_BATCH =
            new org.springframework.data.redis.core.script.DefaultRedisScript<>("""
                    if redis.call('EXISTS', KEYS[2]) == 0 then return {} end
                    local values = redis.call('HMGET', KEYS[1], unpack(ARGV))
                    local result = {}
                    for i = 1, #ARGV do result[i] = values[i] and '1' or '0' end
                    return result
                    """, List.class);

    public long groupMemberCount(String appKey, String groupId) {
        if (hasGroupMemberInit(appKey, groupId)) {
            Long zcard = infra.stringRedisTemplate.opsForZSet().zCard(
                    CacheConstant.buildGroupUserCacheKey(appKey, groupId));
            return zcard == null ? 0L : zcard;
        }
        return countFromDb(JdbcSqlDialectHolder.countGroupUsersByGroup(),
                GroupUserEntity.Fields.groupId, groupId, appKey);
    }

    public long userGroupCount(String appKey, String userId) {
        if (hasUserGroupsInit(appKey, userId)) {
            Long zcard = infra.stringRedisTemplate.opsForZSet().zCard(
                    CacheConstant.buildUserGroupsCacheKey(appKey, userId));
            return zcard == null ? 0L : zcard;
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
        int cap = MessageConstant.GROUP_ROSTER_FULL_LOAD_LIMIT;
        int fetchLimit = cap + 1;
        try {
            List<GroupUserEntity> mysqlList = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectAllGroupUser())
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .param("limit", fetchLimit)
                    .query(GroupUserEntity.class)
                    .list();
            if (mysqlList == null) {
                return List.of();
            }
            if (mysqlList.size() > cap) {
                throw new GroupMembershipLoadException(
                        "群成员超过回源上限 groupId=" + groupId + " size>" + cap);
            }
            return mysqlList;
        } catch (GroupMembershipLoadException e) {
            throw e;
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
        String initKey = CacheConstant.buildGroupUserInitCacheKey(appKey, groupId);
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
        Long ok = infra.stringRedisTemplate.execute(script,
                List.of(zsetKey, versionKey, initKey, CacheConstant.buildRelationRosterTmpCacheKey(zsetKey)),
                args.toArray());
        if (ok == null || ok != 1L) {
            log.warn("群成员回源 CAS 未命中 appKey={} groupId={} expectedVersion={}", appKey, groupId, expectedVersion);
            return false;
        }
        return writeShieldHashIfVersionMatch(appKey, groupId, members, expectedVersion);
    }

    private boolean writeShieldHashIfVersionMatch(String appKey, String groupId, List<GroupUserEntity> members,
                                                  String expectedVersion) {
        List<String> args = new ArrayList<>();
        args.add(expectedVersion == null ? "0" : expectedVersion);
        if (members != null) {
            for (GroupUserEntity member : members) {
                if (member != null && member.getUserId() != null
                        && YesOrNo.YES.getCode().equals(member.getShield())) {
                    args.add(member.getUserId());
                }
            }
        }
        Long result = infra.stringRedisTemplate.execute(REBUILD_SHIELD_SCRIPT,
                List.of(CacheConstant.buildGroupShieldCacheKey(appKey, groupId),
                        CacheConstant.buildGroupShieldInitCacheKey(appKey, groupId),
                        CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId)), args.toArray());
        return Long.valueOf(1L).equals(result);
    }

    /** 版本校验、替换和 INIT 在同槽完成，读者不会看到删除旧 Hash 后的半成品。 */
    private static final DefaultRedisScript<Long> REBUILD_SHIELD_SCRIPT = new DefaultRedisScript<>("""
            local version = redis.call('GET', KEYS[3]) or '0'
            if version ~= ARGV[1] then return 0 end
            redis.call('DEL', KEYS[1])
            for i = 2, #ARGV do redis.call('HSET', KEYS[1], ARGV[i], '1') end
            redis.call('SET', KEYS[2], '1')
            return 1
            """, Long.class);

    private boolean hasGroupShieldInit(String appKey, String groupId) {
        return Boolean.TRUE.equals(infra.stringRedisTemplate.hasKey(
                CacheConstant.buildGroupShieldInitCacheKey(appKey, groupId)));
    }

    private void deleteGroupShieldIndex(String appKey, String groupId) {
        infra.stringRedisTemplate.delete(List.of(
                CacheConstant.buildGroupShieldCacheKey(appKey, groupId),
                CacheConstant.buildGroupShieldInitCacheKey(appKey, groupId)));
    }

    private String currentRelationVersion(String appKey, String groupId) {
        String raw = infra.stringRedisTemplate.opsForValue().get(
                CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId));
        return StringUtils.isBlank(raw) ? "0" : raw.trim();
    }

    /** 退群/踢人等关系变更后递增，仅用于名单回源 CAS；入群须与 ZADD 同 pipeline INCR。 */
    public void bumpGroupRelationVersion(String appKey, String groupId) {
        if (StringUtils.isAnyBlank(appKey, groupId)) {
            return;
        }
        infra.stringRedisTemplate.opsForValue().increment(
                CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId));
    }

    /**
     * 热路径移出群成员：ZREM + 版本 + INIT 计数 + 用户加群 ZSET，并清本机/Pub/Sub。DB 由业务层负责。
     */
    public void removeGroupMemberHot(String appKey, String groupId, String memberId) {
        if (StringUtils.isAnyBlank(appKey, groupId, memberId)) {
            return;
        }
        RelationRosterRedis.removeMember(
                infra.stringRedisTemplate,
                CacheConstant.buildGroupUserCacheKey(appKey, groupId),
                CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId),
                CacheConstant.buildGroupUserInitCacheKey(appKey, groupId),
                memberId);
        try {
            RelationRosterRedis.removeMember(
                    infra.stringRedisTemplate,
                    CacheConstant.buildUserGroupsCacheKey(appKey, memberId),
                    CacheConstant.buildUserGroupsRelationVersionCacheKey(appKey, memberId),
                    CacheConstant.buildUserGroupsInitCacheKey(appKey, memberId),
                    groupId);
        } catch (Exception e) {
            log.warn("移除用户加群索引失败 appKey={} groupId={} memberId={}", appKey, groupId, memberId, e);
        }
        infra.redisTemplate.opsForHash().delete(
                CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId),
                CacheConstant.groupUserConfigField(memberId));
        RelationLocalCache.evictGroupMember(appKey, groupId, memberId);
        RelationCacheInvalidatePublisher.publish(
                RelationCacheInvalidateEvent.groupQuit(appKey, groupId, memberId));
    }

    private Set<String> snapshotIdentities(String cacheKey, Set<String> ids) {
        // ids 是本方法私有构建的 HashSet，包装为只读视图即可；避免超大群在 Set.copyOf 时再复制一整份峰值内存。
        Set<String> snap = ids.isEmpty() ? Set.of() : Collections.unmodifiableSet(ids);
        MessageContext.groupUserIdentityCache.put(cacheKey, snap);
        return snap;
    }

    private void rebuildShieldIndexSync(String appKey, String groupId) {
        String flightKey = appKey + ":" + groupId;
        Object lock = SHIELD_REBUILD_LOCKS[Math.floorMod(flightKey.hashCode(), SHIELD_REBUILD_LOCKS.length)];
        synchronized (lock) {
            if (hasGroupShieldInit(appKey, groupId)) {
                return;
            }
            try {
                String versionBefore = currentRelationVersion(appKey, groupId);
                List<GroupUserEntity> dbMembers = loadAllGroupUsersFromAuthority(appKey, groupId);
                writeShieldHashIfVersionMatch(appKey, groupId, dbMembers, versionBefore);
            } catch (Exception e) {
                log.warn("同步重建群屏蔽索引失败 groupId={}", groupId, e);
            }
        }
    }

    public static final class GroupMembershipLoadException extends RuntimeException {
        public GroupMembershipLoadException(String message) {
            super(message);
        }

        public GroupMembershipLoadException(String message, Throwable cause) {
            super(message, cause);
        }
    }

    public GroupUserEntity groupUserEntity(String appKey, String groupId, String memberId) {
        String localKey = CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, memberId);

        // 1. 本地缓存
        GroupUserEntity groupUserEntity = MessageContext.groupUserEntityCache.get(localKey);
        if (groupUserEntity != null) {
            return groupUserEntity;
        }

        // 2. Redis Hash：field=memberId
        Object redisRaw = infra.redisTemplate.opsForHash().get(
                CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId),
                CacheConstant.groupUserConfigField(memberId));
        if (redisRaw instanceof GroupUserEntity redisEntity) {
            fillLocalGroupUserCache(localKey, redisEntity);
            return redisEntity;
        }

        // 3. MySQL 为禁言/屏蔽等权限权威源；Mongo 滞后不得钉死错误状态
        GroupUserEntity fromMysql = queryGroupUserEntityFromDataBase(appKey, groupId, memberId);
        if (fromMysql != null) {
            return fromMysql;
        }

        // 4. MySQL miss 时再尝试 Mongo（须先确认群属于本 appKey，成员文档本身无租户字段）
        try {
            if (getGroupEntity(appKey, groupId) == null) {
                return null;
            }
            MongoGroupUserEntity mongoGroupUser = infra.mongoTemplate.findOne(
                    mongoGroupUserQuery(groupId, memberId),
                    MongoGroupUserEntity.class);
            if (mongoGroupUser != null) {
                groupUserEntity = convertMongoGroupUserToGroupUser(mongoGroupUser);
                updateGroupUserCache(appKey, groupId, groupUserEntity);
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
        List<String> redisMissingMembers = new ArrayList<>();
        for (String memberId : memberIds) {
            if (memberId == null) {
                continue;
            }
            String localKey = CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, memberId);
            GroupUserEntity local = MessageContext.groupUserEntityCache.get(localKey);
            if (local != null) {
                result.put(memberId, local);
                continue;
            }
            redisMissingMembers.add(memberId);
        }
        List<Object> fields = new ArrayList<>(redisMissingMembers.size());
        for (String memberId : redisMissingMembers) {
            fields.add(CacheConstant.groupUserConfigField(memberId));
        }
        List<Object> redisValues = fields.isEmpty()
                ? List.of()
                : infra.redisTemplate.opsForHash().multiGet(
                        CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId), fields);
        List<String> missing = new ArrayList<>();
        for (int i = 0; i < redisMissingMembers.size(); i++) {
            String memberId = redisMissingMembers.get(i);
            Object raw = i < redisValues.size() ? redisValues.get(i) : null;
            if (raw instanceof GroupUserEntity redis) {
                fillLocalGroupUserCache(
                        CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, memberId), redis);
                result.put(memberId, redis);
            } else {
                missing.add(memberId);
            }
        }
        if (missing.isEmpty()) {
            return result;
        }
        try {
            Map<String, GroupUserEntity> cacheWrites = new HashMap<>();
            for (int fromIndex = 0; fromIndex < missing.size(); fromIndex += GROUP_USER_DB_BATCH_SIZE) {
                List<String> batch = missing.subList(fromIndex,
                        Math.min(fromIndex + GROUP_USER_DB_BATCH_SIZE, missing.size()));
                List<GroupUserEntity> rows = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUserBatch())
                        .param(GroupUserEntity.Fields.groupId, groupId)
                        .param("userIds", batch)
                        .param(GroupEntity.Fields.appKey, appKey)
                        .query(GroupUserEntity.class)
                        .list();
                if (rows == null) {
                    continue;
                }
                for (GroupUserEntity row : rows) {
                    if (row == null || row.getUserId() == null) {
                        continue;
                    }
                    String mid = String.valueOf(row.getUserId());
                    fillLocalGroupUserCache(
                            CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, mid), row);
                    cacheWrites.put(CacheConstant.groupUserConfigField(mid), row);
                    result.put(mid, row);
                }
            }
            if (!cacheWrites.isEmpty()) {
                String hashKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId);
                infra.redisTemplate.opsForHash().putAll(hashKey, cacheWrites);
                infra.redisTemplate.expire(hashKey, MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            }
        } catch (Exception e) {
            log.error("批量查询群成员失败 groupId={} missingSize={}", groupId, missing.size(), e);
            // 禁止把“权威源不可用”伪装成“确认无配置”，否则下游会误判为 IM 渠道。
            throw new GroupMembershipLoadException("批量查询群成员失败 groupId=" + groupId, e);
        }
        return result;
    }

    GroupUserEntity queryGroupUserEntityFromDataBase(String appKey, String groupId, String memberId) {
        try {
            GroupUserEntity groupUserEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                    .param(GroupUserEntity.Fields.userId, memberId)
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(GroupUserEntity.class)
                    .optional()
                    .orElse(null);
            if (groupUserEntity != null) {
                updateGroupUserCache(appKey, groupId, groupUserEntity);
            }
            return groupUserEntity;
        } catch (Exception e) {
            log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    public Mono<GroupUserEntity> groupUserEntityReactive(String appKey, String groupId, String memberId) {
        String localKey = CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, memberId);

        // 1. 本地缓存
        GroupUserEntity localCached = MessageContext.groupUserEntityCache.get(localKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }

        // 2. Redis Hash（响应式）：命中只填 L1，L2 写入放到受控执行器且仅回源路径
        return infra.reactiveRedisTemplate.opsForHash().get(
                        CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId),
                        CacheConstant.groupUserConfigField(memberId))
                .flatMap(raw -> {
                    if (!(raw instanceof GroupUserEntity entity)) {
                        return Mono.empty();
                    }
                    fillLocalGroupUserCache(localKey, entity);
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
                                    return writeThroughGroupUserCacheAsync(appKey, groupId, groupUserEntity)
                                            .thenReturn(groupUserEntity);
                                })
                                .switchIfEmpty(
                                        // 4. Mongo 兜底：群必须属于本 appKey
                                        Mono.fromCallable(() -> getGroupEntity(appKey, groupId) != null)
                                                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                                                .filter(Boolean::booleanValue)
                                                .flatMap(ignored -> infra.reactiveMongoTemplate.findOne(
                                                        mongoGroupUserQuery(groupId, memberId),
                                                        MongoGroupUserEntity.class))
                                                .map(this::convertMongoGroupUserToGroupUser)
                                                .flatMap(groupUserEntity -> writeThroughGroupUserCacheAsync(appKey, groupId, groupUserEntity)
                                                        .thenReturn(groupUserEntity))
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                    return Mono.empty();
                });
    }

    private Mono<Void> writeThroughGroupUserCacheAsync(String appKey, String groupId, GroupUserEntity groupUserEntity) {
        return Mono.fromRunnable(() -> updateGroupUserCache(appKey, groupId, groupUserEntity))
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
                .then();
    }

    @SuppressWarnings("unchecked")
    public Set<String> groupManagerAndLeaderUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        String groupId = message.getTo();
        ensureGroupMemberRoster(appKey, groupId);
        return infra.stringRedisTemplate.opsForZSet().rangeByScore(
                CacheConstant.buildGroupUserCacheKey(appKey, groupId),
                GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
    }

    @SuppressWarnings("unchecked")
    public Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getIngress().getAppKey();
        String groupId = message.getTo();
        ensureGroupMemberRoster(appKey, groupId);
        Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost = new HashMap<>();
        Set<ZSetOperations.TypedTuple<String>> tuples = infra.stringRedisTemplate.opsForZSet().rangeByScoreWithScores(
                CacheConstant.buildGroupUserCacheKey(appKey, groupId),
                GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
        if (tuples != null && !tuples.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                groupManagerAndLeaderUsersIdentityAndPost.put(tuple.getValue(), tuple.getScore());
            }
        }
        return groupManagerAndLeaderUsersIdentityAndPost;
    }

    /**
     * 少量身份点查是否在群（@ 校验）。不灌完整名单、不 ensure 全量回源。
     */
    public Set<String> presentInGroup(String appKey, String groupId, Collection<String> memberIds) {
        Set<String> present = new HashSet<>();
        if (StringUtils.isAnyBlank(appKey, groupId) || memberIds == null || memberIds.isEmpty()) {
            return present;
        }
        List<String> unknown = new ArrayList<>();
        for (String memberId : memberIds) {
            if (StringUtils.isBlank(memberId)) {
                continue;
            }
            Boolean cached = RelationLocalCache.GROUP_MEMBER.get(
                    RelationLocalCache.groupMemberKey(appKey, groupId, memberId));
            if (Boolean.TRUE.equals(cached)) {
                present.add(memberId);
            } else if (!Boolean.FALSE.equals(cached)) {
                unknown.add(memberId);
            }
        }
        if (unknown.isEmpty()) {
            return present;
        }
        String zsetKey = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        List<String> dbUnknown = new ArrayList<>();
        boolean redisAvailable = true;
        try {
            int initState = groupMemberInitState(appKey, groupId);
            if (RelationRosterRedis.isError(initState)) {
                throw new GroupMembershipLoadException(
                        "Redis 无法确认群成员名单状态, appKey=" + appKey + ", groupId=" + groupId);
            }
            boolean initComplete = RelationRosterRedis.isComplete(initState);
            List<Double> scores = infra.stringRedisTemplate.opsForZSet()
                    .score(zsetKey, unknown.toArray(String[]::new));
            for (int index = 0; index < unknown.size(); index++) {
                String memberId = unknown.get(index);
                Double score = scores != null && index < scores.size() ? scores.get(index) : null;
                if (score != null) {
                    RelationLocalCache.markGroupMember(appKey, groupId, memberId, true);
                    present.add(memberId);
                } else if (initComplete) {
                    RelationLocalCache.markGroupMember(appKey, groupId, memberId, false);
                } else {
                    dbUnknown.add(memberId);
                }
            }
        } catch (Exception e) {
            redisAvailable = false;
            log.error("Redis 批量查询群成员异常 appKey={} groupId={} count={}",
                    appKey, groupId, unknown.size(), e);
            dbUnknown.addAll(unknown);
        }
        if (dbUnknown.isEmpty()) {
            return present;
        }
        Map<String, GroupUserEntity> rows = groupUserEntitiesBatch(appKey, groupId, dbUnknown);
        for (String memberId : dbUnknown) {
            boolean in = rows.containsKey(memberId);
            // Redis 故障后的数据库正结果可以缓存；否定结果留待 Redis 恢复后重新确认。
            if (redisAvailable || in) {
                RelationLocalCache.markGroupMember(appKey, groupId, memberId, in);
            }
            if (in) {
                present.add(memberId);
            }
        }
        return present;
    }

    @SuppressWarnings("unchecked")
    public boolean inGroup(String appKey, String from, String groupId) {
        Boolean cached = RelationLocalCache.GROUP_MEMBER.get(RelationLocalCache.groupMemberKey(appKey, groupId, from));
        if (cached != null) {
            return cached;
        }
        String zsetKey = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        boolean redisAvailable = true;
        try {
            Double score = infra.stringRedisTemplate.opsForZSet().score(zsetKey, from);
            if (score != null) {
                RelationLocalCache.markGroupMember(appKey, groupId, from, true);
                return true;
            }
            int initState = groupMemberInitState(appKey, groupId);
            if (RelationRosterRedis.isError(initState)) {
                throw new GroupMembershipLoadException(
                        "Redis 无法确认群成员名单状态, appKey=" + appKey + ", groupId=" + groupId);
            }
            if (RelationRosterRedis.isComplete(initState)) {
                RelationLocalCache.markGroupMember(appKey, groupId, from, false);
                return false;
            }
        } catch (Exception e) {
            redisAvailable = false;
            log.error("Redis 查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, from, e);
        }
        Boolean dbMember = loadGroupMemberExistsFromDb(appKey, groupId, from);
        if (dbMember == null) {
            throw new GroupMembershipLoadException(
                    "无法从权威源确认群成员关系, appKey=" + appKey
                            + ", groupId=" + groupId + ", memberId=" + from);
        }
        if (redisAvailable || Boolean.TRUE.equals(dbMember)) {
            RelationLocalCache.markGroupMember(appKey, groupId, from, dbMember);
        }
        return dbMember;
    }

    /**
     * @return true 在群，false 不在群，null 查询异常（不得缓存 false）
     */
    private Boolean loadGroupMemberExistsFromDb(String appKey, String groupId, String memberId) {
        try {
            GroupUserEntity groupUserEntity = infra.jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
                    .param(GroupUserEntity.Fields.userId, memberId)
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .param(GroupEntity.Fields.appKey, appKey)
                    .query(GroupUserEntity.class)
                    .optional()
                    .orElse(null);
            if (groupUserEntity != null) {
                updateGroupUserCache(appKey, groupId, groupUserEntity);
                cacheMemberPositive(appKey, groupId, memberId, groupUserEntity.getPost());
                return true;
            }
            return false;
        } catch (Exception e) {
            log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
            return null;
        }
    }

    /**
     * 点查确认在群后 ZADD 该成员。INIT 缺失时不创建标记；已完整时同步递增计数。
     */
    private void cacheMemberPositive(String appKey, String groupId, String memberId, Integer post) {
        if (StringUtils.isAnyBlank(appKey, groupId, memberId)) {
            return;
        }
        double score = post == null ? GroupUserPost.ORDINARY.value() : post;
        try {
            RelationRosterRedis.addMember(
                    infra.stringRedisTemplate,
                    CacheConstant.buildGroupUserCacheKey(appKey, groupId),
                    CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId),
                    CacheConstant.buildGroupUserInitCacheKey(appKey, groupId),
                    score,
                    memberId);
        } catch (Exception e) {
            log.warn("回写群成员正缓存失败 appKey={} groupId={} memberId={}", appKey, groupId, memberId, e);
        }
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

        // MySQL 权威：miss/已删除不再回落 Mongo，避免 del_flag 滞后把解散群当活群
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
        // 注意：Supplier 中的逻辑会在 subscribeOn 指定的线程池中执行
        return Mono.fromSupplier(() -> getGroupEntityFromDatabases(appKey, groupId))
                // 3. 切换到专用线程池执行同步任务（关键：避免阻塞 Reactor 核心线程）
                .subscribeOn(Schedulers.fromExecutor(infra.dbExecutor()))
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

    public BindGroupEnum autoPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime,
                                           int maxMembers, int maxPerUser) {
        packet.getMessage().ensureMetadata().setRequestEventContext(
                com.ouyunc.base.model.RequestEventContext.fromSession(groupRequestSession));
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(),
                groupRequestSession.getSessionId(), expireTime, maxMembers, maxPerUser, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getIngress().getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public BindGroupEnum manualPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime,
                                             int maxMembers, int maxPerUser) {
        packet.getMessage().ensureMetadata().setRequestEventContext(
                com.ouyunc.base.model.RequestEventContext.fromSession(groupRequestSession));
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(),
                groupRequestSession.getSessionId(), expireTime, maxMembers, maxPerUser, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getIngress().getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public boolean saveJoinGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        packet.getMessage().ensureMetadata().setRequestEventContext(
                com.ouyunc.base.model.RequestEventContext.fromSession(groupRequestSession));
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getIngress().getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
    }

    public boolean saveGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        packet.getMessage().ensureMetadata().setRequestEventContext(
                com.ouyunc.base.model.RequestEventContext.fromSession(groupRequestSession));
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection) -> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getIngress().getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = session.serializeOrNull(infra.stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = session.serializeOrNull(infra.valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    public <K, V> boolean saveGroupRequestMessage(Packet packet, String groupId, String requestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return session.saveMessageWithSession(packet, expireTime, CacheConstant.buildGroupRequestSessionCacheKey(metadata.getIngress().getAppKey(), groupId, requestSessionId), consumer, (ops, msg, ak, f, t) -> {
        });
    }

    /**
     * 先在群聚合槽原子判定「已是成员 / 容量 / 写入」，再写用户加群索引（超限回滚群侧），最后写请求会话。
     * 两侧不在同槽。仅明确容量拒绝可按 owner 原子回滚；写入结果未知时保留关系供重入核对。
     */
    public BindGroupEnum bindGroup(Packet packet, String joiner, String groupId, String requestSessionId,
                                   long expireTime, int maxMembers, int maxPerUser,
                                   Consumer<RedisConnection> consumer) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return BindGroupEnum.FAILED;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getIngress().getAppKey();
        if (StringUtils.isAnyBlank(appKey, joiner, groupId)) {
            return BindGroupEnum.FAILED;
        }
        try {
            ensureGroupMemberRoster(appKey, groupId);
        } catch (Exception e) {
            log.error("入群前重建成员名单失败 appKey={} groupId={}", appKey, groupId, e);
            return BindGroupEnum.FAILED;
        }
        if (maxMembers >= 0 && !hasGroupMemberInit(appKey, groupId)) {
            log.error("群成员名单未就绪，拒绝带容量入群 appKey={} groupId={}", appKey, groupId);
            return BindGroupEnum.FAILED;
        }
        if (maxPerUser >= 0 && !hasUserGroupsInit(appKey, joiner)
                && userGroupCount(appKey, joiner) >= maxPerUser) {
            return BindGroupEnum.USER_GROUP_LIMIT;
        }

        String owner = java.util.UUID.randomUUID().toString();
        String groupRoster = CacheConstant.buildGroupUserCacheKey(appKey, groupId);
        String groupVersion = CacheConstant.buildGroupRelationVersionCacheKey(appKey, groupId);
        String groupInit = CacheConstant.buildGroupUserInitCacheKey(appKey, groupId);
        String userRoster = CacheConstant.buildUserGroupsCacheKey(appKey, joiner);
        try {
            long groupAdd = RelationRosterRedis.reserveMember(infra.stringRedisTemplate,
                    groupRoster, groupVersion, groupInit, GroupUserPost.ORDINARY.value(), joiner, maxMembers, owner);
            if (groupAdd == RelationRosterRedis.ADD_CAPACITY_EXCEEDED) {
                return BindGroupEnum.GROUP_FULL;
            }
            // 已有群侧关系也必须检查用户侧容量，不能用无条件 add 绕过个人群数上限。
            long userAdd = RelationRosterRedis.reserveMember(infra.stringRedisTemplate, userRoster,
                    CacheConstant.buildUserGroupsRelationVersionCacheKey(appKey, joiner),
                    CacheConstant.buildUserGroupsInitCacheKey(appKey, joiner),
                    metadata.getIngress().getServerTime(), groupId, maxPerUser, owner);
            if (userAdd == RelationRosterRedis.ADD_CAPACITY_EXCEEDED) {
                if (groupAdd == RelationRosterRedis.ADD_NEW) {
                    RelationRosterRedis.rollbackReservedMember(infra.stringRedisTemplate,
                            groupRoster, groupVersion, groupInit, joiner, owner);
                }
                return BindGroupEnum.USER_GROUP_LIMIT;
            }
            // 冻结先于持久化。即使旧请求暂停、新请求碰到容量上限，也不得删除已进入提交阶段的关系。
            if (!RelationRosterRedis.protectReservedMember(infra.stringRedisTemplate, groupRoster, joiner)
                    || !RelationRosterRedis.protectReservedMember(infra.stringRedisTemplate, userRoster, groupId)) {
                return BindGroupEnum.FAILED;
            }
            com.ouyunc.base.constant.enums.SaveMessageOutcomeEnum outcome = session.saveMessageWithSessionOutcome(
                    packet, expireTime, CacheConstant.buildGroupRequestSessionCacheKey(appKey, groupId, requestSessionId),
                    consumer, (ops, msg, ak, f, t) -> { });
            if (!SessionMessagePersistenceSupport.isSaveAccepted(outcome)) {
                // 网络异常、部分 Pipeline 写入或提交确认未知时保留预留，供同一请求重入。
                // 绝不能根据进程内布尔值删除另一执行者已提交的关系。
                return BindGroupEnum.FAILED;
            }
            if (!RelationRosterRedis.confirmReservedMember(infra.stringRedisTemplate, groupRoster, joiner)
                    || !RelationRosterRedis.confirmReservedMember(infra.stringRedisTemplate, userRoster, groupId)) {
                return BindGroupEnum.FAILED;
            }
            RelationLocalCache.onGroupJoin(appKey, groupId, joiner);
            RelationCacheInvalidatePublisher.publish(RelationCacheInvalidateEvent.groupJoin(appKey, groupId, joiner));
            return groupAdd == RelationRosterRedis.ADD_NEW ? BindGroupEnum.SUCCESS : BindGroupEnum.ALREADY_MEMBER;
        } catch (Exception e) {
            log.error("绑定群关系结果未知，保留预留等待重试 appKey={} groupId={} joiner={}", appKey, groupId, joiner, e);
            return BindGroupEnum.FAILED;
        }
    }

    /**
     * 已是群成员时补齐用户侧反向索引，供幂等重试继续完成跨槽关系写入。
     */
    public boolean repairUserGroupIndex(String appKey, String userId, String groupId, long score) {
        if (StringUtils.isAnyBlank(appKey, userId, groupId)) {
            return false;
        }
        try {
            RelationRosterRedis.addMember(
                    infra.stringRedisTemplate,
                    CacheConstant.buildUserGroupsCacheKey(appKey, userId),
                    CacheConstant.buildUserGroupsRelationVersionCacheKey(appKey, userId),
                    CacheConstant.buildUserGroupsInitCacheKey(appKey, userId), score, groupId);
            return true;
        } catch (Exception e) {
            log.error("修复用户群反向索引失败 appKey={} groupId={} userId={}", appKey, groupId, userId, e);
            return false;
        }
    }


    /**
     * 群成员配置写入同群 Hash。整份 Hash 共用 TTL，刷新任一成员会顺延过期时间。
     */
    public void updateGroupUserCache(String appKey, String groupId, GroupUserEntity groupUserEntity) {
        if (groupUserEntity == null || groupUserEntity.getUserId() == null) {
            return;
        }
        String memberId = String.valueOf(groupUserEntity.getUserId());
        fillLocalGroupUserCache(
                CacheConstant.buildGroupUserConfigLocalCacheKey(appKey, groupId, memberId), groupUserEntity);
        String hashKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, groupId);
        infra.redisTemplate.opsForHash().put(hashKey, CacheConstant.groupUserConfigField(memberId), groupUserEntity);
        infra.redisTemplate.expire(hashKey, MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
    }

    /** 仅填本地缓存，不写 L2（Redis 命中路径） */
    private void fillLocalGroupUserCache(String cacheKey, GroupUserEntity groupUserEntity) {
        if (groupUserEntity != null) {
            MessageContext.groupUserEntityCache.put(cacheKey, groupUserEntity);
        }
    }

    private static Query mongoGroupUserQuery(String groupId, String memberId) {
        return Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId)));
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
