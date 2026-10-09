package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 关系名单 Redis：INIT 存基数并与 ZCARD 对齐；增量加/删与版本 CAS 同槽。
 */
public final class RelationRosterRedis {

    public static final int INIT_MISSING = 0;
    public static final int INIT_COMPLETE = 1;
    public static final int INIT_PARTIAL = 2;
    /** Redis 无法确认 INIT 状态；不得按“缺失”触发数据库重建。 */
    public static final int INIT_ERROR = -1;

    public static final long ADD_CAPACITY_EXCEEDED = 0L;
    public static final long ADD_NEW = 1L;
    public static final long ADD_EXISTS = 2L;

    private static final DefaultRedisScript<Long> ADD_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_ADD_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>(
            "redis.call('HDEL', KEYS[4], ARGV[1])\n"
                    + LuaScriptEnum.RELATION_ROSTER_REMOVE_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> UPDATE_SCORE_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_UPDATE_SCORE_SCRIPT.getScript(), Long.class);
    /** 仅未提交的新增关系记录 owner；并发重试接管后，旧调用不能回滚新调用的关系。 */
    private static final DefaultRedisScript<Long> RESERVE_SCRIPT = new DefaultRedisScript<>(
            "local function add()\n" + LuaScriptEnum.RELATION_ROSTER_ADD_IF_CAPACITY_SCRIPT.getScript()
                    + "\nend\nlocal result = add()\n"
                    + "local owner = redis.call('HGET', KEYS[4], ARGV[2])\n"
                    + "if result == 1 or (result == 2 and owner and owner ~= 'P') then\n"
                    + " redis.call('HSET', KEYS[4], ARGV[2], ARGV[4])\n redis.call('PEXPIRE', KEYS[4], ARGV[5])\n return 1\nend\nreturn result", Long.class);
    /** 任一执行者进入持久化前先冻结关系；之后任何容量失败路径都不能再回滚它。 */
    private static final DefaultRedisScript<Long> PROTECT_RESERVED_SCRIPT = new DefaultRedisScript<>("""
            if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
            if redis.call('HEXISTS', KEYS[2], ARGV[1]) == 1 then
                redis.call('HSET', KEYS[2], ARGV[1], 'P')
            end
            return 1
            """, Long.class);
    /** 所有权比较与删除关系在同槽一次执行，禁止先 GET 再删。 */
    private static final DefaultRedisScript<Long> ROLLBACK_RESERVED_SCRIPT = new DefaultRedisScript<>(
            "if redis.call('HGET', KEYS[4], ARGV[1]) ~= ARGV[2] then return 0 end\n"
                    + "redis.call('HDEL', KEYS[4], ARGV[1])\n"
                    + LuaScriptEnum.RELATION_ROSTER_REMOVE_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> CONFIRM_RESERVED_SCRIPT = new DefaultRedisScript<>("""
            if not redis.call('ZSCORE', KEYS[1], ARGV[1]) then return 0 end
            redis.call('HDEL', KEYS[2], ARGV[1])
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CHECK_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_INIT_CHECK_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> FRIEND_REBUILD_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.FRIEND_ROSTER_REBUILD_CAS_SCRIPT.getScript(), Long.class);
    private static final byte[] BLACKLIST_REBUILD_SCRIPT_BYTES =
            LuaScriptEnum.BLACKLIST_REBUILD_CAS_SCRIPT.getScript().getBytes(StandardCharsets.UTF_8);

    private static final byte[] ADD_SCRIPT_BYTES =
            LuaScriptEnum.RELATION_ROSTER_ADD_SCRIPT.getScript().getBytes(StandardCharsets.UTF_8);
    private static final byte[] REMOVE_SCRIPT_BYTES =
            REMOVE_SCRIPT.getScriptAsString().getBytes(StandardCharsets.UTF_8);

    private RelationRosterRedis() {
    }

    /** 与名单同槽；只保存尚未确认的新增关系，不为已有正式成员建立补偿状态。 */
    private static String reservationKey(String rosterKey) {
        return CacheConstant.buildRelationReservationCacheKey(rosterKey);
    }

    public static long reserveMember(StringRedisTemplate redis, String roster, String version,
                                     String init, double score, String member, int limit, String owner) {
        Long result = redis.execute(RESERVE_SCRIPT, List.of(roster, version, init, reservationKey(roster)),
                String.valueOf(score), member, String.valueOf(limit), owner,
                String.valueOf(MessageContext.messageRecoveryTtlMillis()));
        if (result == null) {
            throw new IllegalStateException("关系预留结果未知");
        }
        return result;
    }

    public static void rollbackReservedMember(StringRedisTemplate redis, String roster, String version,
                                              String init, String member, String owner) {
        redis.execute(ROLLBACK_RESERVED_SCRIPT, List.of(roster, version, init, reservationKey(roster)), member, owner);
    }

    /** 两侧关系均保护成功后才可发出持久化命令；保护失败意味着关系已经被并发移除。 */
    public static boolean protectReservedMember(StringRedisTemplate redis, String roster, String member) {
        return Long.valueOf(1L).equals(redis.execute(PROTECT_RESERVED_SCRIPT,
                List.of(roster, reservationKey(roster)), member));
    }

    public static boolean confirmReservedMember(StringRedisTemplate redis, String roster, String member) {
        return Long.valueOf(1L).equals(redis.execute(CONFIRM_RESERVED_SCRIPT,
                List.of(roster, reservationKey(roster)), member));
    }

    public static boolean isComplete(int code) {
        return code == INIT_COMPLETE;
    }

    public static boolean skipRebuild(int code) {
        return code != INIT_MISSING;
    }

    public static boolean isError(int code) {
        return code == INIT_ERROR;
    }

    public static int checkInit(StringRedisTemplate template, String zsetKey, String initKey) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, initKey)) {
            return INIT_MISSING;
        }
        try {
            Long code = template.execute(CHECK_SCRIPT, List.of(zsetKey, initKey));
            if (code == null) {
                return INIT_MISSING;
            }
            int value = code.intValue();
            if (value == INIT_COMPLETE || value == INIT_PARTIAL) {
                return value;
            }
            return INIT_MISSING;
        } catch (Exception e) {
            return INIT_ERROR;
        }
    }

    public static void addMember(StringRedisTemplate template, String zsetKey, String versionKey,
                                 String initKey, double score, String member) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, versionKey, initKey, member)) {
            return;
        }
        template.execute(ADD_SCRIPT, List.of(zsetKey, versionKey, initKey),
                String.valueOf(score), member);
    }

    public static void removeMember(StringRedisTemplate template, String zsetKey, String versionKey,
                                    String initKey, String member) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, versionKey, initKey, member)) {
            return;
        }
        template.execute(REMOVE_SCRIPT, List.of(zsetKey, versionKey, initKey, reservationKey(zsetKey)), member);
    }

    /**
     * 仅更新已存在 member 的 score，不抬版本。
     */
    public static void updateScore(StringRedisTemplate template, String zsetKey, double score, String member) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, member)) {
            return;
        }
        template.execute(UPDATE_SCORE_SCRIPT, List.of(zsetKey), String.valueOf(score), member);
    }

    public static boolean rebuildFriendCas(StringRedisTemplate template, String zsetKey, String versionKey,
                                           String initKey, Object[] args) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, versionKey, initKey) || args == null) {
            return false;
        }
        Long ok = template.execute(FRIEND_REBUILD_SCRIPT,
                List.of(zsetKey, versionKey, initKey, CacheConstant.buildRelationRosterTmpCacheKey(zsetKey)), args);
        return ok != null && ok == 1L;
    }

    /**
     * 黑名单 Hash CAS 重建。field 用 string 序列化，value 用 Redis valueSerializer，
     * 与业务层 HPUT Long 同形态，避免 Lua 写明文后 HGET 反序列化失败。
     */
    @SuppressWarnings("unchecked")
    public static boolean rebuildBlacklistCas(RedisTemplate<String, ?> template,
                                             RedisSerializer<String> stringSerializer,
                                             RedisSerializer<?> valueSerializer,
                                             String hashKey, String initKey, Map<String, Long> fields) {
        if (template == null || stringSerializer == null || valueSerializer == null
                || StringUtils.isAnyBlank(hashKey, initKey)) {
            return false;
        }
        RedisSerializer<Object> valueSer = (RedisSerializer<Object>) valueSerializer;
        Map<String, Long> safe = fields == null ? Map.of() : fields;
        Object result = template.execute((RedisConnection connection) -> {
            List<byte[]> pairs = new ArrayList<>(safe.size() * 2);
            for (Map.Entry<String, Long> entry : safe.entrySet()) {
                if (entry == null || StringUtils.isBlank(entry.getKey())) {
                    continue;
                }
                pairs.add(stringSerializer.serialize(entry.getKey()));
                Long joinTime = entry.getValue() == null ? 1L : entry.getValue();
                pairs.add(valueSer.serialize(joinTime));
            }
            int pairCount = pairs.size() / 2;
            List<byte[]> keysAndArgs = new ArrayList<>(4 + pairs.size());
            keysAndArgs.add(stringSerializer.serialize(hashKey));
            keysAndArgs.add(stringSerializer.serialize(initKey));
            keysAndArgs.add(stringSerializer.serialize(CacheConstant.buildRelationRosterTmpCacheKey(hashKey)));
            keysAndArgs.add(stringSerializer.serialize(String.valueOf(pairCount)));
            keysAndArgs.addAll(pairs);
            return connection.scriptingCommands().eval(
                    BLACKLIST_REBUILD_SCRIPT_BYTES,
                    ReturnType.INTEGER,
                    3,
                    keysAndArgs.toArray(new byte[0][]));
        });
        return result instanceof Number number && number.longValue() == 1L;
    }

    /**
     * Pipeline 内 EVAL 增量加入，与会话热写同连接。
     */
    public static void evalAdd(RedisConnection connection, RedisSerializer<String> stringSerializer,
                               String zsetKey, String versionKey, String initKey, double score, String member) {
        if (connection == null || stringSerializer == null
                || StringUtils.isAnyBlank(zsetKey, versionKey, initKey, member)) {
            return;
        }
        connection.scriptingCommands().eval(
                ADD_SCRIPT_BYTES,
                ReturnType.INTEGER,
                3,
                stringSerializer.serialize(zsetKey),
                stringSerializer.serialize(versionKey),
                stringSerializer.serialize(initKey),
                stringSerializer.serialize(String.valueOf(score)),
                stringSerializer.serialize(member));
    }

    /**
     * Pipeline 内 EVAL 移除，与解散群批量 ZREM 同连接。
     */
    public static void evalRemove(RedisConnection connection, RedisSerializer<String> stringSerializer,
                                  String zsetKey, String versionKey, String initKey, String member) {
        if (connection == null || stringSerializer == null
                || StringUtils.isAnyBlank(zsetKey, versionKey, initKey, member)) {
            return;
        }
        connection.scriptingCommands().eval(
                REMOVE_SCRIPT_BYTES,
                ReturnType.INTEGER,
                4,
                stringSerializer.serialize(zsetKey),
                stringSerializer.serialize(versionKey),
                stringSerializer.serialize(initKey),
                stringSerializer.serialize(reservationKey(zsetKey)),
                stringSerializer.serialize(member));
    }
}
