package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.ImRouteCodec;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.cluster.lease.NodeLeaseKeeper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.ReturnType;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 同 identity 槽原子写路由 HASH + 登录 String。连接计数不在本类，见 {@link com.ouyunc.message.cluster.lease.LocalNodeConnCounter}。
 */
public final class LoginSessionDirectory {

    private static final Logger log = LoggerFactory.getLogger(LoginSessionDirectory.class);

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * KEYS: route, login；ARGV: deviceField, encoded, loginPayload。
     */
    private static final byte[] BIND_LUA = (
            "redis.call('HSET', KEYS[1], ARGV[1], ARGV[2]) "
                    + "redis.call('SET', KEYS[2], ARGV[3]) "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS: route, login；ARGV: deviceField, deleteLogin(0/1)。
     */
    private static final byte[] UNBIND_LUA = (
            "local removed = redis.call('HDEL', KEYS[1], ARGV[1]) "
                    + "if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end "
                    + "if ARGV[2] == '1' then redis.call('DEL', KEYS[2]) end "
                    + "return removed"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * KEYS: route, login1..n；ARGV: field1..n。Cluster 要求脚本访问的 key 全部出现在 KEYS 且同槽。
     */
    private static final byte[] EVICT_DEAD_LUA = (
            "local i = 2 "
                    + "local a = 1 "
                    + "while i <= #KEYS do "
                    + "redis.call('HDEL', KEYS[1], ARGV[a]) "
                    + "redis.call('DEL', KEYS[i]) "
                    + "i = i + 1 "
                    + "a = a + 1 "
                    + "end "
                    + "if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    private static final Object SHA_LOCK = new Object();

    private static volatile String bindSha;

    private static volatile String unbindSha;

    private static volatile String evictSha;

    private LoginSessionDirectory() {
    }

    public static void bind(LoginClientInfo loginClientInfo, String comboIdentity) {
        String nodeId = NodeLeaseKeeper.localNodeId();
        long epoch = NodeLeaseKeeper.currentEpoch();
        loginClientInfo.setNodeEpoch(epoch);
        String routeKey = CacheConstant.buildLoginRouteCacheKey(loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        String loginKey = CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity);
        String encoded = ImRouteCodec.encode(nodeId, epoch);
        evalCached(BIND_LUA, ScriptKind.BIND, 2,
                bytes(routeKey), bytes(loginKey),
                bytes(String.valueOf(loginClientInfo.getDeviceType())),
                bytes(encoded), serializeLogin(loginClientInfo.copyForRedis()));
    }

    public static void unbind(LoginClientInfo loginClientInfo, String comboIdentity) {
        unbindInternal(loginClientInfo, comboIdentity, true);
    }

    /**
     * MQTT 持久会话：只摘路由，保留 login String。
     */
    public static void unbindRouteKeepLogin(LoginClientInfo loginClientInfo) {
        unbindInternal(loginClientInfo, null, false);
    }

    /**
     * 读路径发现死 epoch 时惰性清理。MQTT 持久会话若仍靠登录 String，节点死后也会被摘掉（与 kill-9 一致）。
     */
    public static void evictDeadRoute(String appKey, String identity, Map<?, ?> routeHash, Map<String, Long> liveEpochs) {
        Set<Byte> dead = ImSessionPresence.deadDeviceTypes(routeHash, liveEpochs);
        if (dead.isEmpty()) {
            return;
        }
        try {
            String routeKey = CacheConstant.buildLoginRouteCacheKey(appKey, identity);
            List<byte[]> keysAndArgs = new ArrayList<>(1 + dead.size() * 2);
            List<byte[]> fields = new ArrayList<>(dead.size());
            keysAndArgs.add(bytes(routeKey));
            for (Byte deviceType : dead) {
                String combo = IdentityUtil.generalComboIdentity(appKey, identity, deviceType);
                keysAndArgs.add(bytes(CacheConstant.buildLoginCacheKey(appKey, combo)));
                fields.add(bytes(String.valueOf(deviceType)));
            }
            keysAndArgs.addAll(fields);
            evalCached(EVICT_DEAD_LUA, ScriptKind.EVICT, 1 + dead.size(), keysAndArgs.toArray(byte[][]::new));
        } catch (Exception e) {
            log.warn("惰性清理死路由失败 identity={}", identity, e);
        }
    }

    private static void unbindInternal(LoginClientInfo loginClientInfo, String comboIdentity, boolean deleteLogin) {
        String routeKey = CacheConstant.buildLoginRouteCacheKey(loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        String loginKey = deleteLogin
                ? CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity)
                : routeKey;
        evalCached(UNBIND_LUA, ScriptKind.UNBIND, 2,
                bytes(routeKey), bytes(loginKey),
                bytes(String.valueOf(loginClientInfo.getDeviceType())),
                bytes(deleteLogin ? "1" : "0"));
    }

    /**
     * 登录风暴走 EVALSHA；脚本被 FLUSH 后回退 SCRIPT LOAD / EVAL。
     */
    private static Object evalCached(byte[] script, ScriptKind kind, int keyCount, byte[]... keysAndArgs) {
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

    private static Object evalSha(String sha, int keyCount, byte[]... keysAndArgs) {
        return stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                connection.scriptingCommands().evalSha(sha, ReturnType.VALUE, keyCount, keysAndArgs));
    }

    private static Object evalRaw(byte[] script, int keyCount, byte[]... keysAndArgs) {
        return stringRedisTemplate.execute((RedisCallback<Object>) connection ->
                connection.scriptingCommands().eval(script, ReturnType.VALUE, keyCount, keysAndArgs));
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

    private static byte[] serializeLogin(LoginClientInfo loginClientInfo) {
        @SuppressWarnings("unchecked")
        RedisSerializer<Object> objSer = (RedisSerializer<Object>) redisTemplate.getValueSerializer();
        byte[] raw = objSer.serialize(loginClientInfo);
        if (raw == null) {
            throw new IllegalStateException("登录对象序列化失败");
        }
        return raw;
    }
}
