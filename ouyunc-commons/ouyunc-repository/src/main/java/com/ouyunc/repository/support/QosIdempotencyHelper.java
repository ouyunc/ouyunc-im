package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * QoS 幂等状态机。
 *
 * <p>记录格式 {@code STATE|serverPacketId|payloadHash|ownerToken|clientMessageId|timestamp}：
 * <ul>
 *   <li>{@code PENDING}：已占位、消息尚未确认落库，绝不能作为成功回 ACK；</li>
 *   <li>{@code COMMITTED}：消息 Pipeline 已写入成功，才可作为重复/成功返回。</li>
 * </ul>
 *
 * <p>服务端 packet 键与稳定客户端键（登录身份 + 客户端 messageId）在同一个 Lua 内原子抢占：
 * 任一维度已 COMMITTED 即 DUPLICATE；同键不同正文（payloadHash 不一致）即 CONFLICT，拒绝写入。
 * 释放一律 compare-and-delete（owner + serverPacketId 比对），不会误删其他请求的占位或已提交记录。
 *
 * <p>崩溃残留的 PENDING 在 {@link #PENDING_TAKEOVER_MS} 之后可被同正文的重发接管（可补偿：
 * 接管方用新 serverPacketId 重写同一消息体并 commit；接管前已写入的半截旧 packetId 属于
 * 热 key/会话索引的最终一致残留，不影响“占位不等于成功”的 ACK 语义）。
 */
public final class QosIdempotencyHelper {

    private static final Logger log = LoggerFactory.getLogger(QosIdempotencyHelper.class);

    /** 抢占成功，本次请求持有占位，允许写消息，写完必须 commit。 */
    public static final int CLAIM_ACQUIRED = 1;
    /** 已提交的重复请求，可安全回 ACK，无需再写。 */
    public static final int CLAIM_COMMITTED = 2;
    /** 占位被他人持有（在途），不是成功，不能回 ACK。 */
    public static final int CLAIM_PENDING = 3;
    /** 同键不同正文，明确冲突，拒绝写入。 */
    public static final int CLAIM_CONFLICT = 4;
    /** 无可用幂等维度或 Redis 失败。 */
    public static final int CLAIM_FAILED = 0;

    private static final String PENDING = "PENDING";
    private static final String COMMITTED = "COMMITTED";
    /** 空 clientMessageId 的占位符，避免记录出现空字段导致 Lua 解析错位。 */
    private static final String NO_CLIENT_ID = "-";
    /** PENDING 超过该时长可被同正文重发接管（原持有者视为崩溃），毫秒。 */
    private static final long PENDING_TAKEOVER_MS = 30_000L;
    /** 记录 TTL 下限，必须大于接管窗口，防止接管方 commit 前键过期。 */
    private static final long MIN_RECORD_TTL_MS = 90_000L;

    /**
     * 记录字段以 '|' 分隔。正文哈希与 owner 令牌均为服务端生成的十六进制串，
     * clientMessageId 是客户端可控字符串，写入侧已去掉 '|'，Lua 按分隔符解析不会错位。
     */
    private static final String PARSE_LUA = """
            local function parse(raw)
              local f = {}
              local last = 1
              for i = 1, #raw do
                if string.sub(raw, i, i) == '|' then
                  f[#f + 1] = string.sub(raw, last, i - 1)
                  last = i + 1
                end
              end
              f[#f + 1] = string.sub(raw, last)
              return f
            end
            """;

    /**
     * KEYS 为实际存在的幂等键（1~2 个，均带 {@code {appKey}} 哈希标签，同槽，集群合法）。
     * 时间戳一律取 Redis 服务器时间（redis.call('TIME')），接管判定不受应用节点间时钟偏差影响。
     * ARGV: [hash, owner, serverId, clientId, takeoverMs, ttlMs...]（ttlMs 与 KEYS 一一对应）。
     */
    private static final DefaultRedisScript<Long> CLAIM_SCRIPT = script(PARSE_LUA + """
            local hash = ARGV[1]
            local owner = ARGV[2]
            local serverId = ARGV[3]
            local clientId = ARGV[4]
            local takeoverMs = tonumber(ARGV[5])
            local now = redis.call('TIME')
            now = now[1] * 1000 + math.floor(now[2] / 1000)
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if raw then
                local f = parse(raw)
                if hash ~= '' and f[3] ~= hash then return 4 end
                if f[1] == 'COMMITTED' then return 2 end
                if f[1] == 'PENDING' then
                  local ts = tonumber(f[6])
                  if ts == nil or now - ts <= takeoverMs then return 3 end
                else return 4 end
              end
            end
            local record = table.concat({'PENDING', serverId, hash, owner, clientId, tostring(now)}, '|')
            for i = 1, #KEYS do
              redis.call('PSETEX', KEYS[i], tonumber(ARGV[5 + i]), record)
            end
            return 1
            """);

    /** 只读判定，用于重发判重。ARGV: [hash]。COMMITTED + 同正文即重复，不绑定 packetId（重发链路 packetId 可能被服务端重排）。 */
    private static final DefaultRedisScript<Long> STATE_SCRIPT = script(PARSE_LUA + """
            local hash = ARGV[1]
            local pendingSeen = false
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if raw then
                local f = parse(raw)
                if hash ~= '' and f[3] ~= hash then return 4 end
                if f[1] == 'COMMITTED' then return 2 end
                if f[1] == 'PENDING' then pendingSeen = true end
              end
            end
            return pendingSeen and 3 or 0
            """);

    /**
     * KEYS 同抢占。ARGV: [owner, serverId, hash, ttlMs...]。时间戳取 Redis 服务器时间。
     * 仅同 owner 同 serverId 同 hash 的 PENDING 可转 COMMITTED；已是同值 COMMITTED 视为幂等成功。
     */
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = script(PARSE_LUA + """
            local owner = ARGV[1]
            local serverId = ARGV[2]
            local hash = ARGV[3]
            local nowParts = redis.call('TIME')
            local now = nowParts[1] * 1000 + math.floor(nowParts[2] / 1000)
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if not raw then return 0 end
              local f = parse(raw)
              if f[1] == 'COMMITTED' then
                if f[2] ~= serverId or f[3] ~= hash then return 0 end
              elseif f[1] == 'PENDING' and f[2] == serverId and f[3] == hash and f[4] == owner then
                redis.call('PSETEX', KEYS[i], tonumber(ARGV[3 + i]),
                  table.concat({'COMMITTED', serverId, hash, owner, f[5], tostring(now)}, '|'))
              else return 0 end
            end
            return 1
            """);

    /**
     * KEYS 同抢占。ARGV: [owner, serverId]。compare-and-delete：
     * 仅删除同 owner 同 serverId 的 PENDING，绝不触碰他人占位或已提交记录。
     */
    private static final DefaultRedisScript<Long> RELEASE_SCRIPT = script(PARSE_LUA + """
            local owner = ARGV[1]
            local serverId = ARGV[2]
            local removed = 0
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if raw then
                local f = parse(raw)
                if f[1] == 'PENDING' and f[2] == serverId and f[4] == owner then
                  redis.call('DEL', KEYS[i])
                  removed = removed + 1
                end
              end
            end
            return removed
            """);

    /** 脚本参数/键统一按 String 序列化，避免误用模板的 Jackson value 序列化器读写原始记录。 */
    private static final RedisSerializer<String> STRING_SERIALIZER = RedisSerializer.string();

    private QosIdempotencyHelper() {
    }

    /**
     * 读取幂等记录状态。仅 {@code COMMITTED} 且正文哈希一致才算“已成功”；
     * {@code PENDING}、冲突、缺失均不是成功，调用方不得据此回 ACK。
     */
    public static int checkState(RedisTemplate<String, ?> redisTemplate, Packet packet, String channelLoginIdentity) {
        if (redisTemplate == null || packet == null || packet.getMessage() == null) {
            return CLAIM_FAILED;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isBlank(metadata.getAppKey())) {
            return CLAIM_FAILED;
        }
        List<String> keys = claimKeys(metadata.getAppKey(), packet.getPacketId(),
                resolveClaimIdentity(message, channelLoginIdentity), message.getId());
        if (keys.isEmpty()) {
            return CLAIM_FAILED;
        }
        Long result = eval(redisTemplate, STATE_SCRIPT, keys, payloadHash(message));
        return toClaimState(result);
    }

    /**
     * 仅 {@code COMMITTED} 可视为重复并安全回 ACK。PENDING 表示占位但未落库，不是成功。
     */
    public static boolean isDuplicate(RedisTemplate<String, ?> redisTemplate, Packet packet,
                                      String channelLoginIdentity) {
        return checkState(redisTemplate, packet, channelLoginIdentity) == CLAIM_COMMITTED;
    }

    /**
     * 原子抢占 packet 键与稳定 client 键。只有返回 {@link #CLAIM_ACQUIRED} 才允许写消息。
     *
     * @param ownerToken 本次请求的持有者令牌，commit/release 必须原值带回
     * @param message    用于计算正文哈希的消息体
     */
    public static int tryClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                               String loginIdentity, String clientMessageId, String ownerToken, Message message) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return CLAIM_FAILED;
        }
        ClaimTarget target = claimTarget(appKey, packetId, loginIdentity, clientMessageId);
        if (target.keys.isEmpty()) {
            return CLAIM_FAILED;
        }
        List<String> args = new ArrayList<>(5 + target.ttls.size());
        args.add(payloadHash(message));
        args.add(ownerToken);
        args.add(String.valueOf(packetId));
        args.add(normalizeClientId(clientMessageId));
        args.add(String.valueOf(PENDING_TAKEOVER_MS));
        args.addAll(target.ttls);
        return toClaimState(eval(redisTemplate, CLAIM_SCRIPT, target.keys, args.toArray(new String[0])));
    }

    public static String newOwnerToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 将当前 owner 的 {@code PENDING} 原子转为 {@code COMMITTED}。返回 false 表示占位已丢失、
     * 过期或被接管，本次写入不能作为成功回 ACK。
     */
    public static boolean commit(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                 String loginIdentity, String clientMessageId, String ownerToken, Message message) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return false;
        }
        ClaimTarget target = claimTarget(appKey, packetId, loginIdentity, clientMessageId);
        if (target.keys.isEmpty()) {
            return false;
        }
        List<String> args = new ArrayList<>(3 + target.ttls.size());
        args.add(ownerToken);
        args.add(String.valueOf(packetId));
        args.add(payloadHash(message));
        args.addAll(target.ttls);
        Long result = eval(redisTemplate, COMMIT_SCRIPT, target.keys, args.toArray(new String[0]));
        return result != null && result == 1L;
    }

    /**
     * 比对删除：仅删除同 owner 同 serverPacketId 的 {@code PENDING}，绝不触碰他人占位与已提交记录。
     * {@code ownerToken} 为空时直接 no-op（宁可留 PENDING 等接管，也不误删）。
     */
    public static void releaseClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                    String loginIdentity, String clientMessageId, String ownerToken) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return;
        }
        List<String> keys = claimKeys(appKey, packetId, loginIdentity, clientMessageId);
        if (keys.isEmpty()) {
            return;
        }
        eval(redisTemplate, RELEASE_SCRIPT, keys, ownerToken, String.valueOf(packetId));
    }

    /**
     * 正文指纹：只覆盖客户端可见、服务端落库链路不改写的字段，保证“同键不同正文”可判定，
     * 同时重发（同一正文）能对上哈希。
     */
    public static String payloadHash(Message message) {
        if (message == null) {
            return "";
        }
        String source = String.join("\u0001",
                nullSafe(message.getId()),
                nullSafe(message.getTo()),
                String.valueOf(message.getToType()),
                String.valueOf(message.getContentType()),
                sanitize(message.getContent()),
                String.valueOf(message.getCreateTime()),
                nullSafe(message.getCorrelationId()));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(source.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 判重使用的客户端身份：元数据 &gt; 当前通道登录身份 &gt; message.from。 */
    public static String resolveClaimIdentity(Message message, String channelLoginIdentity) {
        if (message == null) {
            return channelLoginIdentity;
        }
        Metadata metadata = message.getMetadata();
        if (metadata != null && StringUtils.isNotBlank(metadata.getQosClaimIdentity())) {
            return metadata.getQosClaimIdentity();
        }
        if (StringUtils.isNotBlank(channelLoginIdentity)) {
            return channelLoginIdentity;
        }
        return message.getFrom();
    }

    private static int toClaimState(Long result) {
        if (result == null) {
            return CLAIM_FAILED;
        }
        return switch (result.intValue()) {
            case 1 -> CLAIM_ACQUIRED;
            case 2 -> CLAIM_COMMITTED;
            case 3 -> CLAIM_PENDING;
            case 4 -> CLAIM_CONFLICT;
            default -> CLAIM_FAILED;
        };
    }

    private static ClaimTarget claimTarget(String appKey, long packetId, String loginIdentity, String clientMessageId) {
        ClaimTarget target = new ClaimTarget();
        String pktKey = packetKey(appKey, packetId);
        if (pktKey != null) {
            target.keys.add(pktKey);
            target.ttls.add(String.valueOf(ttlMs(MessageConstant.CACHE_QOS_IDEM_PACKET_EXPIRE_TIMESTAMP)));
        }
        String cliKey = clientKey(appKey, loginIdentity, clientMessageId);
        if (cliKey != null) {
            target.keys.add(cliKey);
            target.ttls.add(String.valueOf(ttlMs(MessageConstant.CACHE_QOS_IDEM_CLIENT_EXPIRE_TIMESTAMP)));
        }
        return target;
    }

    private static List<String> claimKeys(String appKey, long packetId, String loginIdentity, String clientMessageId) {
        List<String> keys = new ArrayList<>(2);
        String pktKey = packetKey(appKey, packetId);
        if (pktKey != null) {
            keys.add(pktKey);
        }
        String cliKey = clientKey(appKey, loginIdentity, clientMessageId);
        if (cliKey != null) {
            keys.add(cliKey);
        }
        return keys;
    }

    private static String packetKey(String appKey, long packetId) {
        if (StringUtils.isBlank(appKey) || packetId <= 0) {
            return null;
        }
        return CacheConstant.buildQosIdempotencyPacketKey(appKey, packetId);
    }

    private static String clientKey(String appKey, String loginIdentity, String clientMessageId) {
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(loginIdentity) || StringUtils.isBlank(clientMessageId)) {
            return null;
        }
        return CacheConstant.buildQosIdempotencyClientKey(appKey, loginIdentity, clientMessageId);
    }

    private static long ttlMs(long configuredMs) {
        return Math.max(configuredMs, MIN_RECORD_TTL_MS);
    }

    private static String normalizeClientId(String clientMessageId) {
        return StringUtils.isBlank(clientMessageId) ? NO_CLIENT_ID : sanitize(clientMessageId);
    }

    /** 记录以 '|' 分字段，客户端可控字符串必须去掉分隔符，避免 Lua 解析错位。 */
    private static String sanitize(String value) {
        return value == null ? "" : value.replace('|', '_');
    }

    /**
     * EVALSHA（Spring 侧按脚本 SHA 缓存，NOSCRIPT 自动回源 EVAL）。执行失败按“无记录”处理，
     * 上层以 FAILED 语义兜底，不在幂等链路里向外抛 Redis 异常。
     */
    @SuppressWarnings("unchecked")
    private static Long eval(RedisTemplate<String, ?> template, DefaultRedisScript<Long> script,
                             List<String> keys, String... args) {
        try {
            return template.execute(script, STRING_SERIALIZER, null, keys, (Object[]) args);
        } catch (Exception e) {
            log.warn("QoS 幂等脚本执行失败: {}", e.getMessage());
            return null;
        }
    }

    private static String nullSafe(String value) {
        return value == null ? "" : value;
    }

    private static DefaultRedisScript<Long> script(String body) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(body);
        script.setResultType(Long.class);
        return script;
    }

    private static final class ClaimTarget {
        private final List<String> keys = new ArrayList<>(2);
        private final List<String> ttls = new ArrayList<>(2);
    }
}
