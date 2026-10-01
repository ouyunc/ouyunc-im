package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ImRouteCodec;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.cluster.lease.NodeLeaseSnapshot;
import com.ouyunc.message.cluster.lease.SessionNodeState;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 登录会话的 Redis 轻量路由目录。
 * <p>完整登录上下文只保存在落地节点的 Channel 属性中；Redis 仅保存
 * {@code deviceType -> nodeId|nodeEpoch|lastLoginTime}，不再创建或续期登录详情 String。</p>
 */
public final class LoginSessionDirectoryHelper {

    private static final Logger log = LoggerFactory.getLogger(LoginSessionDirectoryHelper.class);

    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * KEYS[1]=route；ARGV: deviceField, encoded, lastLoginTime。
     * 路由末段 lastLoginTime 更大则拒绝覆盖，避免迟到的旧登录覆盖新会话。
     */
    private static final byte[] BIND_LUA = (
            "local cur = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "local ts = tonumber(ARGV[3]) "
                    + "if cur and ts ~= nil then "
                    + "local bars = 0 "
                    + "for i = 1, #cur do "
                    + "if string.sub(cur, i, i) == '|' then bars = bars + 1 end "
                    + "end "
                    + "if bars >= 2 then "
                    + "local existingTs = tonumber(string.match(cur, '(%d+)$')) "
                    + "if existingTs and existingTs > ts then return 0 end "
                    + "end "
                    + "end "
                    + "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * 仅当字段仍等于本连接的完整 fencing value 时删除，防止旧 Channel 关闭误删新登录。
     */
    private static final byte[] UNBIND_LUA = (
            "local cur = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "if not cur or cur ~= ARGV[2] then return 0 end "
                    + "local removed = redis.call('HDEL', KEYS[1], ARGV[1]) "
                    + "if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end "
                    + "return removed"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS[1]=route；ARGV: field1, expectedRoute1...。
     * 仅当前路由仍等于读快照时删除，避免旧清理误删刚完成的新登录。
     */
    private static final byte[] EVICT_DEAD_LUA = (
            "local a = 1 "
                    + "while a <= #ARGV do "
                    + "local cur = redis.call('HGET', KEYS[1], ARGV[a]) "
                    + "if cur and cur == ARGV[a + 1] then "
                    + "redis.call('HDEL', KEYS[1], ARGV[a]) "
                    + "end "
                    + "a = a + 2 "
                    + "end "
                    + "if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    private static final Object SHA_LOCK = new Object();

    private static volatile String bindSha;

    private static volatile String unbindSha;

    private static volatile String evictSha;

    private LoginSessionDirectoryHelper() {
    }

    public static void bind(LoginClientInfo loginClientInfo) {
        String nodeId = SessionNodeState.localNodeId();
        long epoch = SessionNodeState.currentEpoch();
        loginClientInfo.setNodeEpoch(epoch);
        String routeKey = CacheConstant.buildLoginRouteCacheKey(loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        String encoded = ImRouteCodec.encode(nodeId, epoch, loginClientInfo.getLastLoginTime());
        Long result = evalCached(BIND_LUA, ScriptKind.BIND, 1,
                bytes(routeKey),
                bytes(String.valueOf(loginClientInfo.getDeviceType())),
                bytes(encoded), bytes(String.valueOf(loginClientInfo.getLastLoginTime())));
        if (!Long.valueOf(1L).equals(result)) {
            throw new MessageException("登录绑定失败：已有更新会话");
        }
    }

    public static void unbind(LoginClientInfo loginClientInfo) {
        unbindInternal(loginClientInfo);
    }

    /**
     * 读路径发现死 epoch 时惰性清理。
     */
    public static void evictDeadRoute(String appKey, String identity, Map<?, ?> routeHash,
                                     NodeLeaseSnapshot snapshot) {
        // Redis 失联或启动中只有本地信息，不能把这种不完整视图作为删除远端路由的证据。
        if (!SessionNodeState.isCurrentSnapshot(snapshot)) {
            return;
        }
        Set<Byte> dead = ImSessionPresence.deadDeviceTypes(routeHash, snapshot.epochs());
        if (dead.isEmpty()) {
            return;
        }
        try {
            String routeKey = CacheConstant.buildLoginRouteCacheKey(appKey, identity);
            List<byte[]> keysAndArgs = new ArrayList<>(1 + dead.size() * 2);
            List<byte[]> fieldsAndExpected = new ArrayList<>(dead.size() * 2);
            keysAndArgs.add(bytes(routeKey));
            for (Byte deviceType : dead) {
                String field = String.valueOf(deviceType);
                String expectedRoute = routeValue(routeHash, field);
                if (expectedRoute == null) {
                    continue;
                }
                fieldsAndExpected.add(bytes(field));
                fieldsAndExpected.add(bytes(expectedRoute));
            }
            if (fieldsAndExpected.isEmpty()) {
                return;
            }
            keysAndArgs.addAll(fieldsAndExpected);
            // 构造参数期间可能跨过有效期或已刷新为新的成员视图，旧判断一律放弃。
            if (SessionNodeState.isCurrentSnapshot(snapshot)) {
                evalCached(EVICT_DEAD_LUA, ScriptKind.EVICT, 1, keysAndArgs.toArray(byte[][]::new));
            }
        } catch (Exception e) {
            log.warn("惰性清理死路由失败 identity={}", identity, e);
        }
    }

    private static void unbindInternal(LoginClientInfo loginClientInfo) {
        String routeKey = CacheConstant.buildLoginRouteCacheKey(loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        String expectedRoute = ImRouteCodec.encode(loginClientInfo.getLoginServerAddress(),
                loginClientInfo.getNodeEpoch(), loginClientInfo.getLastLoginTime());
        evalCached(UNBIND_LUA, ScriptKind.UNBIND, 1,
                bytes(routeKey),
                bytes(String.valueOf(loginClientInfo.getDeviceType())),
                bytes(expectedRoute));
    }

    /**
     * 登录风暴走 EVALSHA；脚本被 FLUSH 后回退 SCRIPT LOAD / EVAL。
     */
    private static Long evalCached(byte[] script, ScriptKind kind, int keyCount, byte[]... keysAndArgs) {
        String sha = shaOf(script, kind);
        try {
            return evalSha(sha, keyCount, keysAndArgs);
        } catch (Exception first) {
            if (!isNoScript(first)) {
                throw first;
            }
            invalidateSha(kind);
            sha = shaOf(script, kind);
            try {
                return evalSha(sha, keyCount, keysAndArgs);
            } catch (Exception second) {
                if (!isNoScript(second)) {
                    throw second;
                }
                log.warn("EVALSHA 仍 NOSCRIPT，回退 EVAL kind={}", kind);
                return evalRaw(script, keyCount, keysAndArgs);
            }
        }
    }

    private static String shaOf(byte[] script, ScriptKind kind) {
        String sha = shaOfKind(kind);
        if (sha != null) {
            return sha;
        }
        synchronized (SHA_LOCK) {
            sha = shaOfKind(kind);
            if (sha != null) {
                return sha;
            }
            sha = stringRedisTemplate.execute((RedisCallback<String>) connection ->
                    connection.scriptingCommands().scriptLoad(script));
            if (sha == null) {
                throw new IllegalStateException("SCRIPT LOAD 返回空 SHA");
            }
            storeSha(kind, sha);
            return sha;
        }
    }

    private static String shaOfKind(ScriptKind kind) {
        return switch (kind) {
            case BIND -> bindSha;
            case UNBIND -> unbindSha;
            case EVICT -> evictSha;
        };
    }

    private static void storeSha(ScriptKind kind, String sha) {
        switch (kind) {
            case BIND -> bindSha = sha;
            case UNBIND -> unbindSha = sha;
            case EVICT -> evictSha = sha;
        }
    }

    private static void invalidateSha(ScriptKind kind) {
        synchronized (SHA_LOCK) {
            storeSha(kind, null);
        }
    }

    private enum ScriptKind {
        BIND,
        UNBIND,
        EVICT
    }

    /**
     * 通过 SHA 执行登录目录脚本。当前脚本统一返回 Redis Integer Reply，必须使用 INTEGER 解码；
     * 若误用 VALUE，Lettuce 会以 ValueOutput 解码整数并抛出 UnsupportedOperationException。
     */
    private static Long evalSha(String sha, int keyCount, byte[]... keysAndArgs) {
        return stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                (Long) connection.scriptingCommands().evalSha(
                        sha, ReturnType.INTEGER, keyCount, keysAndArgs));
    }

    /** SCRIPT FLUSH 后的原始脚本回退路径，返回类型必须与 EVALSHA 路径保持一致。 */
    private static Long evalRaw(byte[] script, int keyCount, byte[]... keysAndArgs) {
        return stringRedisTemplate.execute((RedisCallback<Long>) connection ->
                (Long) connection.scriptingCommands().eval(
                        script, ReturnType.INTEGER, keyCount, keysAndArgs));
    }

    private static boolean isNoScript(Throwable throwable) {
        Throwable cursor = throwable;
        while (cursor != null) {
            String name = cursor.getClass().getName();
            String message = cursor.getMessage();
            if (name.contains("NoScript") || (message != null && message.contains("NOSCRIPT"))) {
                return true;
            }
            cursor = cursor.getCause();
        }
        return false;
    }

    private static byte[] bytes(String value) {
        RedisSerializer<String> strSer = stringRedisTemplate.getStringSerializer();
        byte[] raw = strSer.serialize(value);
        if (raw == null) {
            throw new IllegalStateException("Redis 字符串序列化失败");
        }
        return raw;
    }

    private static String routeValue(Map<?, ?> routeHash, String field) {
        if (routeHash == null || routeHash.isEmpty()) {
            return null;
        }
        for (Map.Entry<?, ?> entry : routeHash.entrySet()) {
            String key = entry.getKey() instanceof byte[] rawKey
                    ? new String(rawKey, StandardCharsets.UTF_8) : String.valueOf(entry.getKey());
            if (!field.equals(key) || entry.getValue() == null) {
                continue;
            }
            return entry.getValue() instanceof byte[] rawValue
                    ? new String(rawValue, StandardCharsets.UTF_8) : String.valueOf(entry.getValue());
        }
        return null;
    }

}
