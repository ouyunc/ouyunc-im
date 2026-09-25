package com.ouyunc.message.helper;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.model.LoginClientInfo;
import com.ouyunc.base.utils.ChannelAttrUtil;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.ImRouteCodec;
import com.ouyunc.base.utils.ImSessionPresence;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.cluster.lease.NodeLeaseSnapshot;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
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
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 同 identity 槽原子写路由 HASH + 登录 String。连接计数不在本类，见 {@link com.ouyunc.message.cluster.lease.LocalNodeConnCounter}。
 */
public final class LoginSessionDirectory {

    private static final Logger log = LoggerFactory.getLogger(LoginSessionDirectory.class);

    private static final RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();

    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * KEYS: route, login；ARGV: deviceField, encoded, loginPayload, lastLoginTime, loginTtlSeconds。
     * 路由末段 lastLoginTime 更大则拒绝覆盖（fencing）。登录 String 带 TTL，由租约心跳续期。
     */
    private static final byte[] BIND_LUA = (
            "local cur = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "local ts = tonumber(ARGV[4]) "
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
                    + "local ttl = tonumber(ARGV[5]) "
                    + "if ttl ~= nil and ttl > 0 then "
                    + "redis.call('SET', KEYS[2], ARGV[3], 'EX', ttl) "
                    + "else "
                    + "redis.call('SET', KEYS[2], ARGV[3]) "
                    + "end "
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
     * KEYS: route, login1..n；ARGV: field1, expectedRoute1..n。
     * 仅当前路由仍等于读快照时删除，避免旧清理误删刚完成的新登录。
     */
    private static final byte[] EVICT_DEAD_LUA = (
            "local i = 2 "
                    + "local a = 1 "
                    + "while i <= #KEYS do "
                    + "local cur = redis.call('HGET', KEYS[1], ARGV[a]) "
                    + "if cur and cur == ARGV[a + 1] then "
                    + "redis.call('HDEL', KEYS[1], ARGV[a]) "
                    + "redis.call('DEL', KEYS[i]) "
                    + "end "
                    + "i = i + 1 "
                    + "a = a + 2 "
                    + "end "
                    + "if redis.call('HLEN', KEYS[1]) == 0 then redis.call('DEL', KEYS[1]) end "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    /**
     * 活跃连接续期与修复：只有路由字段仍等于本连接的完整 fencing 值时才写登录详情。
     * 登录 String 已经过期时会原子重建；跨节点顶号后旧连接无法把新目录覆盖回来。
     */
    private static final byte[] RENEW_LUA = (
            "local cur = redis.call('HGET', KEYS[1], ARGV[1]) "
                    + "if not cur or cur ~= ARGV[2] then return 0 end "
                    + "redis.call('SET', KEYS[2], ARGV[3], 'EX', ARGV[4]) "
                    + "return 1"
    ).getBytes(StandardCharsets.UTF_8);

    private static final Object SHA_LOCK = new Object();

    private static volatile String bindSha;

    private static volatile String unbindSha;

    private static volatile String evictSha;

    private static final AtomicBoolean LOGIN_TTL_RENEW_IN_FLIGHT = new AtomicBoolean(false);

    private static final int LOGIN_TTL_RENEW_BATCH = 200;

    private LoginSessionDirectory() {
    }

    public static void bind(LoginClientInfo loginClientInfo, String comboIdentity) {
        String nodeId = SessionNodeState.localNodeId();
        long epoch = SessionNodeState.currentEpoch();
        loginClientInfo.setNodeEpoch(epoch);
        String routeKey = CacheConstant.buildLoginRouteCacheKey(loginClientInfo.getAppKey(), loginClientInfo.getIdentity());
        String loginKey = CacheConstant.buildLoginCacheKey(loginClientInfo.getAppKey(), comboIdentity);
        String encoded = ImRouteCodec.encode(nodeId, epoch, loginClientInfo.getLastLoginTime());
        Object result = evalCached(BIND_LUA, ScriptKind.BIND, 2,
                bytes(routeKey), bytes(loginKey),
                bytes(String.valueOf(loginClientInfo.getDeviceType())),
                bytes(encoded), serializeLogin(loginClientInfo.copyForRedis()),
                bytes(String.valueOf(loginClientInfo.getLastLoginTime())),
                bytes(String.valueOf(MessageConstant.IM_LOGIN_SESSION_TTL_SECONDS)));
        if (result instanceof Number number && number.longValue() == 0L) {
            throw new MessageException("登录绑定失败：已有更新会话");
        }
    }

    public static void unbind(LoginClientInfo loginClientInfo, String comboIdentity) {
        unbindInternal(loginClientInfo, comboIdentity, true);
    }

    /**
     * 租约心跳联动：为本机仍在线的登录 String 续期。节点死后无人续期，TTL 内幽灵在线消失。
     */
    public static boolean renewLocalLoginTtls() {
        if (!LOGIN_TTL_RENEW_IN_FLIGHT.compareAndSet(false, true)) {
            return false;
        }
        try {
            List<RenewItem> batch = new ArrayList<>(LOGIN_TTL_RENEW_BATCH);
            for (ChannelHandlerContext ctx : MessageServerContext.localLoginClientRegisterTable.asMap().values()) {
                if (ctx == null || ctx.channel() == null || !ctx.channel().isActive()) {
                    continue;
                }
                LoginClientInfo login = ChannelAttrUtil.getChannelAttribute(
                        ctx, MessageConstant.CHANNEL_ATTR_KEY_TAG_LOGIN);
                if (login == null || login.getAppKey() == null || login.getIdentity() == null) {
                    continue;
                }
                String combo = IdentityUtil.generalComboIdentity(
                        login.getAppKey(), login.getIdentity(), login.getDeviceType());
                String expectedRoute = ImRouteCodec.encode(SessionNodeState.localNodeId(),
                        login.getNodeEpoch(), login.getLastLoginTime());
                batch.add(new RenewItem(ctx,
                        CacheConstant.buildLoginRouteCacheKey(login.getAppKey(), login.getIdentity()),
                        CacheConstant.buildLoginCacheKey(login.getAppKey(), combo),
                        String.valueOf(login.getDeviceType()), expectedRoute,
                        serializeLogin(login.copyForRedis())));
                if (batch.size() >= LOGIN_TTL_RENEW_BATCH) {
                    renewBatch(batch);
                    batch.clear();
                }
            }
            if (!batch.isEmpty()) {
                renewBatch(batch);
            }
            return true;
        } catch (Exception e) {
            log.warn("续期或修复本机登录目录失败", e);
            return false;
        } finally {
            LOGIN_TTL_RENEW_IN_FLIGHT.set(false);
        }
    }

    /** 同一条命令中的 route/login key 使用相同 identity hash-tag，可安全运行于 Redis Cluster。 */
    private static void renewBatch(List<RenewItem> batch) {
        List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            for (RenewItem item : batch) {
                connection.scriptingCommands().eval(RENEW_LUA, ReturnType.INTEGER, 2,
                        bytes(item.routeKey()), bytes(item.loginKey()), bytes(item.deviceField()),
                        bytes(item.expectedRoute()), item.loginPayload(),
                        bytes(String.valueOf(MessageConstant.IM_LOGIN_SESSION_TTL_SECONDS)));
            }
            return null;
        });
        if (results == null || results.size() != batch.size()) {
            throw new IllegalStateException("登录目录续期结果数量不完整 expected="
                    + batch.size() + " actual=" + (results == null ? 0 : results.size()));
        }
        for (int index = 0; index < batch.size(); index++) {
            if (Long.valueOf(1L).equals(results.get(index))) {
                continue;
            }
            ChannelHandlerContext ctx = batch.get(index).ctx();
            if (ctx != null && ctx.channel() != null && ctx.channel().isActive()) {
                log.info("本机连接已失去登录目录所有权，关闭旧连接 channel={}", ctx.channel().id());
                ctx.channel().eventLoop().execute(ctx::close);
            }
        }
    }

    private record RenewItem(ChannelHandlerContext ctx, String routeKey, String loginKey,
                             String deviceField, String expectedRoute, byte[] loginPayload) {
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
                String combo = IdentityUtil.generalComboIdentity(appKey, identity, deviceType);
                keysAndArgs.add(bytes(CacheConstant.buildLoginCacheKey(appKey, combo)));
                String field = String.valueOf(deviceType);
                String expectedRoute = routeValue(routeHash, field);
                if (expectedRoute == null) {
                    keysAndArgs.remove(keysAndArgs.size() - 1);
                    continue;
                }
                fieldsAndExpected.add(bytes(field));
                fieldsAndExpected.add(bytes(expectedRoute));
            }
            int loginKeyCount = keysAndArgs.size() - 1;
            if (loginKeyCount == 0) {
                return;
            }
            keysAndArgs.addAll(fieldsAndExpected);
            // 构造参数期间可能跨过有效期或已刷新为新的成员视图，旧判断一律放弃。
            if (SessionNodeState.isCurrentSnapshot(snapshot)) {
                evalCached(EVICT_DEAD_LUA, ScriptKind.EVICT, 1 + loginKeyCount, keysAndArgs.toArray(byte[][]::new));
            }
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
