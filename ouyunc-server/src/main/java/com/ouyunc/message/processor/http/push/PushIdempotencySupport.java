package com.ouyunc.message.processor.http.push;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.message.context.MessageServerContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

/**
 * HTTP 推送幂等（appKey + messageId）状态机。
 *
 * <p>记录格式 {@code STATE|packetId} 或 {@code PENDING|packetId|epochMs}：
 * <ul>
 *   <li>{@code PENDING}：已受理、后台落库/投递进行中；僵死超过接管窗口后可被同 messageId 重新抢占（B3）；</li>
 *   <li>{@code COMMITTED}：主记录持久化成功，同 messageId 返回 DUPLICATE；</li>
 *   <li>{@code RETRYABLE_FAILED}：后台失败，允许同 messageId 重新抢占；</li>
 * </ul>
 * 升级兼容：无 {@code |} 的历史裸 packetId 视为已 COMMITTED，避免双发。
 *
 * <p>{@code ACCEPTED} 产品语义：校验通过并已写入 PENDING，后台异步确认落库；
 * 未 COMMITTED 前可查询到 PROCESSING/RETRYABLE_FAILED，失败或 PENDING 超时后允许安全重试。
 * 按接收人进度由入口 {@code messageId:to} 扇出保证，本键不覆盖多接收人局部成功。
 */
public final class PushIdempotencySupport {

    private static final Logger log = LoggerFactory.getLogger(PushIdempotencySupport.class);

    public static final int CLAIM_ACQUIRED = 1;
    public static final int CLAIM_COMMITTED = 2;
    public static final int CLAIM_PENDING = 3;
    public static final int CLAIM_FAILED = 0;

    public static final String STATE_PENDING = "PENDING";
    public static final String STATE_COMMITTED = "COMMITTED";
    public static final String STATE_RETRYABLE_FAILED = "RETRYABLE_FAILED";

    private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializer.string();

    /**
     * ARGV: packetId, ttlMs, takeoverMs。
     * PENDING 带 epoch；超时可接管。COMMITTED 仍拒绝；RETRYABLE_FAILED / 僵死 PENDING 可重占。
     */
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = script("""
            local raw = redis.call('GET', KEYS[1])
            local packetId = ARGV[1]
            local ttlMs = tonumber(ARGV[2])
            local takeoverMs = tonumber(ARGV[3])
            local nowArr = redis.call('TIME')
            local now = nowArr[1] * 1000 + math.floor(nowArr[2] / 1000)
            if raw then
              local sep = string.find(raw, '|', 1, true)
              if not sep then
                return 2
              end
              local state = string.sub(raw, 1, sep - 1)
              if state == 'COMMITTED' then return 2 end
              if state == 'PENDING' then
                local rest = string.sub(raw, sep + 1)
                local sep2 = string.find(rest, '|', 1, true)
                local ts = nil
                if sep2 then
                  ts = tonumber(string.sub(rest, sep2 + 1))
                end
                if ts ~= nil and now - ts <= takeoverMs then
                  return 3
                end
              end
            end
            redis.call('PSETEX', KEYS[1], ttlMs, 'PENDING|' .. packetId .. '|' .. tostring(now))
            return 1
            """);

    /** PENDING|packetId 或 PENDING|packetId|ts 均可提交。 */
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = script("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local prefix = 'PENDING|' .. ARGV[1]
            if raw ~= prefix and string.sub(raw, 1, #prefix + 1) ~= (prefix .. '|') then return 0 end
            redis.call('PSETEX', KEYS[1], tonumber(ARGV[2]), 'COMMITTED|' .. ARGV[1])
            return 1
            """);

    private static final DefaultRedisScript<Long> RETRYABLE_FAILED_SCRIPT = script("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local prefix = 'PENDING|' .. ARGV[1]
            if raw ~= prefix and string.sub(raw, 1, #prefix + 1) ~= (prefix .. '|') then return 0 end
            redis.call('PSETEX', KEYS[1], tonumber(ARGV[2]), 'RETRYABLE_FAILED|' .. ARGV[1])
            return 1
            """);

    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script("""
            local raw = redis.call('GET', KEYS[1])
            if not raw then return 0 end
            local prefix = 'PENDING|' .. ARGV[1]
            if raw ~= prefix and string.sub(raw, 1, #prefix + 1) ~= (prefix .. '|') then return 0 end
            return redis.call('DEL', KEYS[1])
            """);

    private PushIdempotencySupport() {
    }

    /**
     * 读取状态；无记录返回 null。
     */
    public static IdempotencyRecord getRecord(String appKey, String messageId) {
        if (StringUtils.isAnyBlank(appKey, messageId)) {
            return null;
        }
        RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
        String key = CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId);
        Object value = redisTemplate.opsForValue().get(key);
        if (value == null) {
            return null;
        }
        return parse(String.valueOf(value));
    }

    /** 已 COMMITTED（或历史裸值）时的 packetId；否则 null。 */
    public static String getCommittedPacketId(String appKey, String messageId) {
        IdempotencyRecord record = getRecord(appKey, messageId);
        if (record == null || !STATE_COMMITTED.equals(record.state())) {
            return null;
        }
        return record.packetId();
    }

    /**
     * 抢占 PENDING。返回 {@link #CLAIM_ACQUIRED} 才可触发投递；
     * {@link #CLAIM_COMMITTED} 表示可安全回 DUPLICATE；{@link #CLAIM_PENDING} 表示在途。
     */
    public static int tryClaim(String appKey, String messageId, String packetId) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId)) {
            return CLAIM_FAILED;
        }
        Long result = eval(CLAIM_SCRIPT, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                packetId, String.valueOf(ttlMs()), String.valueOf(MessageConstant.HTTP_PUSH_PENDING_TAKEOVER_MS));
        if (result == null) {
            return CLAIM_FAILED;
        }
        return switch (result.intValue()) {
            case 1 -> CLAIM_ACQUIRED;
            case 2 -> CLAIM_COMMITTED;
            case 3 -> CLAIM_PENDING;
            default -> CLAIM_FAILED;
        };
    }

    /** 主记录持久化成功：PENDING → COMMITTED。 */
    public static boolean commit(String appKey, String messageId, String packetId) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId)) {
            return false;
        }
        Long result = eval(COMMIT_SCRIPT, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                packetId, String.valueOf(ttlMs()));
        return result != null && result == 1L;
    }

    /**
     * 后台落库/投递失败：PENDING → RETRYABLE_FAILED，同 messageId 可重新抢占。
     * 不直接 DEL，便于调用方查询到失败态。
     */
    public static boolean markRetryableFailed(String appKey, String messageId, String packetId) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId)) {
            return false;
        }
        Long result = eval(RETRYABLE_FAILED_SCRIPT, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                packetId, String.valueOf(ttlMs()));
        return result != null && result == 1L;
    }

    /** 投递尚未提交时回滚：仅删除本请求持有的 PENDING。 */
    public static boolean releaseIfOwned(String appKey, String messageId, String packetId) {
        if (StringUtils.isAnyBlank(appKey, messageId, packetId)) {
            return false;
        }
        Long result = eval(RELEASE_SCRIPT, CacheConstant.buildHttpPushIdempotentCacheKey(appKey, messageId),
                packetId);
        return result != null && result > 0;
    }

    /** @deprecated 请使用 {@link #getCommittedPacketId}；保留兼容旧调用。 */
    @Deprecated
    public static String getPacketId(String appKey, String messageId) {
        IdempotencyRecord record = getRecord(appKey, messageId);
        return record == null ? null : record.packetId();
    }

    public record IdempotencyRecord(String state, String packetId) {
    }

    private static IdempotencyRecord parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        int sep = raw.indexOf('|');
        if (sep <= 0) {
            // 历史裸 packetId：按已成功受理处理，避免升级后双发
            return new IdempotencyRecord(STATE_COMMITTED, raw);
        }
        String state = raw.substring(0, sep);
        String rest = raw.substring(sep + 1);
        int sep2 = rest.indexOf('|');
        String packetId = sep2 < 0 ? rest : rest.substring(0, sep2);
        return new IdempotencyRecord(state, packetId);
    }

    private static long ttlMs() {
        long ttlSec = MessageServerContext.serverProperties().getHttpPushIdempotentTtlSeconds();
        if (ttlSec <= 0) {
            ttlSec = 86400L;
        }
        return ttlSec * 1000L;
    }

    @SuppressWarnings("unchecked")
    private static Long eval(DefaultRedisScript<Long> script, String key, String... args) {
        try {
            RedisTemplate<String, Object> redisTemplate = CacheFactory.REDIS.instance();
            return redisTemplate.execute(script, STRING_SERIALIZER, null, List.of(key), (Object[]) args);
        } catch (Exception e) {
            log.warn("HTTP 推送幂等脚本失败 key={} err={}", key, e.getMessage());
            return null;
        }
    }

    private static DefaultRedisScript<Long> script(String body) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(body);
        script.setResultType(Long.class);
        return script;
    }
}
