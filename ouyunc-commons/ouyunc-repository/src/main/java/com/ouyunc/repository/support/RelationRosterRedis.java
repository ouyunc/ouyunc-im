package com.ouyunc.repository.support;

import com.ouyunc.base.constant.enums.LuaScriptEnum;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 关系名单 Redis：INIT 存基数并与 ZCARD 对齐；增量加/删与版本 CAS 同槽。
 */
public final class RelationRosterRedis {

    public static final int INIT_MISSING = 0;
    public static final int INIT_COMPLETE = 1;
    public static final int INIT_PARTIAL = 2;

    private static final DefaultRedisScript<Long> ADD_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_ADD_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> REMOVE_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_REMOVE_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> CHECK_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.RELATION_ROSTER_INIT_CHECK_SCRIPT.getScript(), Long.class);
    private static final DefaultRedisScript<Long> FRIEND_REBUILD_SCRIPT = new DefaultRedisScript<>(
            LuaScriptEnum.FRIEND_ROSTER_REBUILD_CAS_SCRIPT.getScript(), Long.class);

    private static final byte[] ADD_SCRIPT_BYTES =
            LuaScriptEnum.RELATION_ROSTER_ADD_SCRIPT.getScript().getBytes(StandardCharsets.UTF_8);

    private RelationRosterRedis() {
    }

    public static boolean isComplete(int code) {
        return code == INIT_COMPLETE;
    }

    public static boolean skipRebuild(int code) {
        return code != INIT_MISSING;
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
            return INIT_MISSING;
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
        template.execute(REMOVE_SCRIPT, List.of(zsetKey, versionKey, initKey), member);
    }

    public static boolean rebuildFriendCas(StringRedisTemplate template, String zsetKey, String versionKey,
                                           String initKey, Object[] args) {
        if (template == null || StringUtils.isAnyBlank(zsetKey, versionKey, initKey) || args == null) {
            return false;
        }
        Long ok = template.execute(FRIEND_REBUILD_SCRIPT, List.of(zsetKey, versionKey, initKey), args);
        return ok != null && ok == 1L;
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
}
