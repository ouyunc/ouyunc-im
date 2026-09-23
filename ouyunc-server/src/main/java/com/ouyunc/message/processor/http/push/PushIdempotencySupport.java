package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.UUID;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * HTTP 推送幂等状态机。
 * <p>记录格式：{@code STATE|canonicalPacketId|payloadHash|ownerToken|epochMs}。
 * packetId 标识消息，ownerToken 标识本次执行；接管后旧执行者不能修改新占位。</p>
 */
public final class PushIdempotencySupport {
    private static final Logger log = LoggerFactory.getLogger(PushIdempotencySupport.class);
    public static final int CLAIM_FAILED = 0;
    public static final int CLAIM_ACQUIRED = 1;
    public static final int CLAIM_COMMITTED = 2;
    public static final int CLAIM_PENDING = 3;
    public static final int CLAIM_CONFLICT = 4;
    private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializer.string();

    /** ARGV: proposedPacketId, payloadHash, ownerToken, ttlMs, takeoverMs。 */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = listScript("""
            local raw = redis.call('GET', KEYS[1])
            local proposed = ARGV[1]
            local hash = ARGV[2]
            local owner = ARGV[3]
            local ttlMs = tonumber(ARGV[4])
            local takeoverMs = tonumber(ARGV[5])
            local nowArr = redis.call('TIME')
            local now = nowArr[1] * 1000 + math.floor(nowArr[2] / 1000)
            local canonical = proposed
            if raw then
              local fields = {}
              for value in string.gmatch(raw, '([^|]+)') do table.insert(fields, value) end
              if #fields < 5 then return {0, ''} end
              if fields[3] ~= hash then return {4, fields[2]} end
              canonical = fields[2]
              if fields[1] == 'COMMITTED' then return {2, canonical} end
              if fields[1] == 'PENDING' and now - tonumber(fields[5]) <= takeoverMs then
                return {3, canonical}
              end
            end
            redis.call('PSETEX', KEYS[1], ttlMs,
              'PENDING|' .. canonical .. '|' .. hash .. '|' .. owner .. '|' .. tostring(now))
            return {1, canonical}
            """);

    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = longScript("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local expected = 'PENDING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|'
            if string.sub(raw, 1, #expected) ~= expected then return 0 end
            redis.call('PSETEX', KEYS[1], tonumber(ARGV[4]),
              'COMMITTED|' .. ARGV[1] .. '|' .. ARGV[2] .. '|NONE|0')
            return 1
            """);

    private static final DefaultRedisScript<Long> FAIL_SCRIPT = longScript("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local expected = 'PENDING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|'
            if string.sub(raw, 1, #expected) ~= expected then return 0 end
            redis.call('PSETEX', KEYS[1], tonumber(ARGV[4]),
              'RETRYABLE_FAILED|' .. ARGV[1] .. '|' .. ARGV[2] .. '|NONE|0')
            return 1
            """);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = longScript("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local expected = 'PENDING|' .. ARGV[1] .. '|' .. ARGV[2] .. '|' .. ARGV[3] .. '|'
            if string.sub(raw, 1, #expected) ~= expected then return 0 end
            return redis.call('DEL', KEYS[1])
            """);

    private PushIdempotencySupport() {
    }

    /** 原子抢占；指纹不同返回 CONFLICT，仍在处理返回 PENDING。 */
    public static ClaimResult tryClaim(String appKey, String messageId, String packetId, Message message) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId) || message == null) return ClaimResult.failed();
        String hash = message.getMetadata() != null
                ? message.getMetadata().getHttpPushPayloadHash() : null;
        if (StringUtils.isBlank(hash)) {
            hash = payloadHash(message);
        }
        String ownerToken = UUID.randomUUID().toString();
        List<?> raw = evalList(CLAIM_SCRIPT, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                packetId, hash, ownerToken, String.valueOf(ttlMs()),
                String.valueOf(MessageConstant.HTTP_PUSH_PENDING_TAKEOVER_MS));
        if (raw == null || raw.size() < 2) return ClaimResult.failed();
        int state = Integer.parseInt(String.valueOf(raw.get(0)));
        String canonical = String.valueOf(raw.get(1));
        return new ClaimResult(state, canonical, hash, state == CLAIM_ACQUIRED ? ownerToken : null);
    }

    public static boolean commit(String appKey, String messageId, ClaimIdentity claim) {
        return mutate(COMMIT_SCRIPT, appKey, messageId, claim, true);
    }

    public static boolean markRetryableFailed(String appKey, String messageId, ClaimIdentity claim) {
        return mutate(FAIL_SCRIPT, appKey, messageId, claim, true);
    }

    public static boolean releaseIfOwned(String appKey, String messageId, ClaimIdentity claim) {
        return mutate(RELEASE_SCRIPT, appKey, messageId, claim, false);
    }

    private static boolean mutate(DefaultRedisScript<Long> script, String appKey, String messageId,
                                  ClaimIdentity claim, boolean withTtl) {
        if (StringUtils.isAnyBlank(appKey, messageId) || claim == null || !claim.isComplete()) return false;
        Long result = withTtl
                ? evalLong(script, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                    claim.packetId(), claim.payloadHash(), claim.ownerToken(), String.valueOf(ttlMs()))
                : evalLong(script, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                    claim.packetId(), claim.payloadHash(), claim.ownerToken());
        return result != null && result == 1L;
    }

    public record ClaimResult(int state, String canonicalPacketId, String payloadHash, String ownerToken) {
        static ClaimResult failed() { return new ClaimResult(CLAIM_FAILED, null, null, null); }
        public ClaimIdentity identity() { return new ClaimIdentity(canonicalPacketId, payloadHash, ownerToken); }
    }

    public record ClaimIdentity(String packetId, String payloadHash, String ownerToken) {
        public boolean isComplete() { return StringUtils.isNoneBlank(packetId, payloadHash, ownerToken); }
    }

    /** HTTP 请求指纹排除服务端补入的 createTime，其余业务字段全部参与冲突判断。 */
    public static String payloadHash(Message message) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            updateDigest(digest, message.getId());
            updateDigest(digest, message.getFrom());
            updateDigest(digest, String.valueOf(message.getFromType()));
            updateDigest(digest, message.getTo());
            updateDigest(digest, String.valueOf(message.getToType()));
            updateDigest(digest, String.valueOf(message.getContentType()));
            updateDigest(digest, message.getContent());
            updateDigest(digest, message.getExtra());
            updateDigest(digest, String.valueOf(message.getQos()));
            updateDigest(digest, message.getCorrelationId());
            updateDigest(digest, message.getAt());
            updateDigest(digest, message.getRef());
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    private static void updateDigest(MessageDigest digest, String value) {
        if (value == null) {
            digest.update(new byte[]{-1, -1, -1, -1});
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        digest.update(new byte[]{(byte) (bytes.length >>> 24), (byte) (bytes.length >>> 16),
                (byte) (bytes.length >>> 8), (byte) bytes.length});
        digest.update(bytes);
    }

    private static void updateDigest(MessageDigest digest, List<String> values) {
        if (values == null) {
            digest.update(new byte[]{-1, -1, -1, -1});
            return;
        }
        int size = values.size();
        digest.update(new byte[]{(byte) (size >>> 24), (byte) (size >>> 16),
                (byte) (size >>> 8), (byte) size});
        for (String value : values) {
            updateDigest(digest, value);
        }
    }

    private static long ttlMs() {
        long seconds = MessageServerContext.serverProperties().getHttpPushIdempotentTtlSeconds();
        return (seconds > 0 ? seconds : 86_400L) * 1_000L;
    }

    @SuppressWarnings("unchecked")
    private static Long evalLong(DefaultRedisScript<Long> script, String key, String... args) {
        try {
            RedisTemplate<String, Object> redis = CacheFactory.REDIS.instance();
            return redis.execute(script, STRING_SERIALIZER, null, List.of(key), (Object[]) args);
        } catch (Exception e) {
            log.warn("HTTP 推送幂等脚本失败 key={} err={}", key, e.getMessage());
            return null;
        }
    }

    @SuppressWarnings("unchecked")
    private static List<?> evalList(DefaultRedisScript<List> script, String key, String... args) {
        try {
            RedisTemplate<String, Object> redis = CacheFactory.REDIS.instance();
            Object raw = redis.execute(script, STRING_SERIALIZER, null, List.of(key), (Object[]) args);
            return raw instanceof List<?> values ? values : null;
        } catch (Exception e) {
            log.warn("HTTP 推送幂等抢占脚本失败 key={} err={}", key, e.getMessage());
            return null;
        }
    }

    private static DefaultRedisScript<Long> longScript(String body) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(body);
        script.setResultType(Long.class);
        return script;
    }

    @SuppressWarnings("rawtypes")
    private static DefaultRedisScript<List> listScript(String body) {
        DefaultRedisScript<List> script = new DefaultRedisScript<>();
        script.setScriptText(body);
        script.setResultType(List.class);
        return script;
    }
}
