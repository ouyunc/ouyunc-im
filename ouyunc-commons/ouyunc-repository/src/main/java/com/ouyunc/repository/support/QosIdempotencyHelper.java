package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
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
 * <p>客户端只稳定传递 {@code messageId}；{@code packetId} 是服务端内部身份。
 * 幂等权威键为 {@code appKey + loginIdentity + messageId}（client 键），packet 键仅辅助同请求占位。
 * 冷库/SAVE 键是租户级 {@code appKey:messageId}，因此客户端 messageId 必须在同一 appKey 内全局唯一
 * （UUID/ULID/雪花），不能按设备从 1 递增；记录额外保存协议 messageType，避免同 messageId 不同业务被当成重复成功。
 * 服务端正式身份是 claim 得到的 packetId，归档不得早于该对齐。
 *
 * <p>记录格式 {@code STATE|serverPacketId|payloadHash|ownerToken|clientMessageId|timestamp}：
 * <ul>
 *   <li>{@code PENDING}：已占位、消息尚未确认落库，绝不能作为成功回 ACK；</li>
 *   <li>{@code COMMITTED}：消息 Pipeline 已写入成功，才可作为重复/成功返回。
 *       记录中的 {@code serverPacketId} 即该 messageId 的正式 packetId（canonical）。</li>
 * </ul>
 *
 * <p>服务端 packet 键与稳定客户端键在同一个 Lua 内原子抢占：
 * 任一维度已 COMMITTED 即 DUPLICATE，并原子带回 canonical packetId；
     * 同键不同正文（payloadHash 不一致）或消息类型不同即 CONFLICT。
     * 旧记录没有消息类型字段时只比对正文，避免升级后把已提交重试判成冲突。
 * 释放一律 compare-and-delete（owner + serverPacketId 比对）。
 *
 * <p>崩溃残留的 PENDING 在 {@link #PENDING_TAKEOVER_MS} 之后可被同正文的重发接管。
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

    /** QoS 提交结果；UNKNOWN 表示 Redis 可能已执行脚本但响应未返回。 */
    public enum CommitOutcome {
        COMMITTED,
        REJECTED,
        UNKNOWN
    }

    /**
     * 抢占/判重结构化结果。{@link #CLAIM_COMMITTED} 时 {@link #canonicalPacketId()} 为首次正式 packetId；
     * 拿不到正式 ID 时不得当作可 ACK 的重复。
     */
    public record ClaimResult(int state, long canonicalPacketId) {
        public boolean isCommittedWithCanonical() {
            return state == CLAIM_COMMITTED && canonicalPacketId > 0L;
        }
    }

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
     * KEYS 为实际存在的幂等键（1~2 个）。有 loginIdentity 时 packet/client 同槽 {@code {appKey:identity}}（P1）；
     * 仅 packet 键时按 packetId 分片。时间戳一律取 Redis 服务器时间。
     * ARGV: [hash, owner, serverId, clientId, messageType, takeoverMs, ttlMs...]（ttlMs 与 KEYS 一一对应）。
     * 返回 {@code {state, canonicalPacketId}}。第二项必须是<b>字符串</b>：Lua 5.1 的 number 是双精度，
     * 19 位 packetId 超过 2^53 后经 {@code tonumber} 会被舍入，绝不能对 packetId 调用 tonumber。
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> CLAIM_SCRIPT = listScript(PARSE_LUA + """
            local hash = ARGV[1]
            local owner = ARGV[2]
            local serverId = ARGV[3]
            local clientId = ARGV[4]
            local messageType = ARGV[5]
            local takeoverMs = tonumber(ARGV[6])
            local now = redis.call('TIME')
            now = now[1] * 1000 + math.floor(now[2] / 1000)
            local reuseId = nil
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if raw then
                local f = parse(raw)
                if hash ~= '' and f[3] ~= hash then return {4, ''} end
                if f[7] ~= nil and f[7] ~= '' and messageType ~= '' and f[7] ~= messageType then return {4, ''} end
                if f[1] == 'COMMITTED' then
                  return {2, f[2]}
                end
                if f[1] == 'PENDING' then
                  local ts = tonumber(f[6])
                  if f[4] == owner then
                    return {1, f[2]}
                  end
                  if ts ~= nil and now - ts <= takeoverMs then
                    return {3, ''}
                  end
                  if f[2] ~= nil and f[2] ~= '' then reuseId = f[2] end
                else return {4, ''} end
              end
            end
            local sid = reuseId or serverId
            local record = table.concat({'PENDING', sid, hash, owner, clientId, tostring(now), messageType}, '|')
            for i = 1, #KEYS do
              redis.call('PSETEX', KEYS[i], tonumber(ARGV[6 + i]), record)
            end
            return {1, sid}
            """);

    /**
     * 只读判定。COMMITTED + 同正文即重复；canonical 取记录；重发可不带同一 packetId。
     * canonical 同样按字符串返回，不得经 Lua {@code tonumber}。
     */
    @SuppressWarnings("rawtypes")
    private static final DefaultRedisScript<List> STATE_SCRIPT = listScript(PARSE_LUA + """
            local hash = ARGV[1]
            local messageType = ARGV[2]
            local pendingSeen = false
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if raw then
                local f = parse(raw)
                if hash ~= '' and f[3] ~= hash then return {4, ''} end
                if f[7] ~= nil and f[7] ~= '' and messageType ~= '' and f[7] ~= messageType then return {4, ''} end
                if f[1] == 'COMMITTED' then
                  return {2, f[2]}
                end
                if f[1] == 'PENDING' then pendingSeen = true end
              end
            end
            if pendingSeen then return {3, ''} end
            return {0, ''}
            """);

    /**
     * KEYS 同抢占。ARGV: [owner, serverId, hash, messageType, ttlMs...]。时间戳取 Redis 服务器时间。
     * 先校验全部键再统一写入，避免 key1 已 COMMITTED、key2 失败留下幽灵成功记录。
     */
    private static final DefaultRedisScript<Long> COMMIT_SCRIPT = script(PARSE_LUA + """
            local owner = ARGV[1]
            local serverId = ARGV[2]
            local hash = ARGV[3]
            local messageType = ARGV[4]
            local nowParts = redis.call('TIME')
            local now = nowParts[1] * 1000 + math.floor(nowParts[2] / 1000)
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              if not raw then return 0 end
              local f = parse(raw)
              if f[1] == 'COMMITTED' then
                if f[2] ~= serverId or f[3] ~= hash then return 0 end
                if f[7] ~= nil and f[7] ~= '' and messageType ~= '' and f[7] ~= messageType then return 0 end
              elseif not (f[1] == 'PENDING' and f[2] == serverId and f[3] == hash and f[4] == owner) then
                return 0
              elseif f[7] ~= nil and f[7] ~= '' and messageType ~= '' and f[7] ~= messageType then
                return 0
              end
            end
            for i = 1, #KEYS do
              local raw = redis.call('GET', KEYS[i])
              local f = parse(raw)
              if f[1] == 'PENDING' then
                redis.call('PSETEX', KEYS[i], tonumber(ARGV[4 + i]),
                  table.concat({'COMMITTED', serverId, hash, owner, f[5], tostring(now), f[7] or messageType}, '|'))
              end
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
        return checkStateResult(redisTemplate, packet, channelLoginIdentity).state();
    }

    /**
     * 结构化判重：COMMITTED 时原子带回正式 packetId，避免二次 GET。
     */
    public static ClaimResult checkStateResult(RedisTemplate<String, ?> redisTemplate, Packet packet,
                                               String channelLoginIdentity) {
        if (redisTemplate == null || packet == null || packet.getMessage() == null) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isBlank(metadata.getAppKey())) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        List<String> keys = claimKeys(metadata.getAppKey(), packet.getPacketId(),
                resolveClaimIdentity(message, channelLoginIdentity), message.getId());
        if (keys.isEmpty()) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        return toClaimResult(evalList(redisTemplate, STATE_SCRIPT, keys,
                payloadHash(message), String.valueOf(packet.getMessageType())));
    }

    /**
     * 仅当 COMMITTED 且拿到正式 packetId 时可视为重复并安全回 ACK。
     * 成功时将 {@code packet.packetId} 收敛为正式 ID（客户端无感知，服务端索引一致）。
     */
    public static boolean isDuplicate(RedisTemplate<String, ?> redisTemplate, Packet packet,
                                      String channelLoginIdentity) {
        ClaimResult result = checkStateResult(redisTemplate, packet, channelLoginIdentity);
        if (!result.isCommittedWithCanonical()) {
            return false;
        }
        packet.setPacketId(result.canonicalPacketId());
        return true;
    }

    /**
     * 稳定 client-messageId 已提交时返回首次服务端 packetId；优先使用 {@link #checkStateResult}/{@link #tryClaimResult}。
     */
    public static Long committedPacketId(RedisTemplate<String, ?> redisTemplate, String appKey,
                                         String loginIdentity, String clientMessageId) {
        String key = clientKey(appKey, loginIdentity, clientMessageId);
        if (redisTemplate == null || key == null) {
            return null;
        }
        try {
            byte[] raw = redisTemplate.execute((RedisCallback<byte[]>) connection ->
                    connection.stringCommands().get(STRING_SERIALIZER.serialize(key)));
            if (raw == null) {
                return null;
            }
            String[] fields = new String(raw, StandardCharsets.UTF_8).split("\\|", -1);
            if (fields.length < 2 || !"COMMITTED".equals(fields[0])) {
                return null;
            }
            return Long.parseLong(fields[1]);
        } catch (Exception e) {
            log.warn("读取 QoS canonical packetId 失败 appKey={} clientMessageId={}",
                    appKey, clientMessageId, e);
            return null;
        }
    }

    /**
     * 原子抢占 packet 键与稳定 client 键。只有返回 {@link #CLAIM_ACQUIRED} 才允许写消息。
     */
    public static int tryClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                               String loginIdentity, String clientMessageId, String ownerToken, Message message,
                               byte messageType) {
        return tryClaimResult(redisTemplate, appKey, packetId, loginIdentity, clientMessageId, ownerToken, message,
                messageType).state();
    }

    /**
     * 结构化抢占：COMMITTED 时原子带回正式 packetId。
     */
    public static ClaimResult tryClaimResult(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                             String loginIdentity, String clientMessageId, String ownerToken,
                                             Message message, byte messageType) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        ClaimTarget target = claimTarget(appKey, packetId, loginIdentity, clientMessageId);
        if (target.keys.isEmpty()) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        List<String> args = new ArrayList<>(5 + target.ttls.size());
        args.add(payloadHash(message));
        args.add(ownerToken);
        args.add(String.valueOf(packetId));
        args.add(normalizeClientId(clientMessageId));
        args.add(String.valueOf(messageType));
        args.add(String.valueOf(PENDING_TAKEOVER_MS));
        args.addAll(target.ttls);
        return toClaimResult(evalList(redisTemplate, CLAIM_SCRIPT, target.keys, args.toArray(new String[0])));
    }

    public static String newOwnerToken() {
        return UUID.randomUUID().toString();
    }

    /**
     * 将当前 owner 的 {@code PENDING} 原子转为 {@code COMMITTED}，并区分明确拒绝与结果未知。
     */
    public static CommitOutcome commit(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                       String loginIdentity, String clientMessageId, String ownerToken, Message message,
                                       byte messageType) {
        return commit(redisTemplate, appKey, packetId, packetId, loginIdentity, clientMessageId, ownerToken, message,
                messageType);
    }

    /**
     * 接管重发时占位键仍按 {@code claimKeyPacketId} 定位，记录内的正式 ID 是 {@code recordPacketId}，
     * 两者不同，不能用对齐后的 canonical ID 去算键，否则 commit 找不到自己的 PENDING。
     */
    public static CommitOutcome commit(RedisTemplate<String, ?> redisTemplate, String appKey,
                                       long claimKeyPacketId, long recordPacketId,
                                       String loginIdentity, String clientMessageId, String ownerToken, Message message,
                                       byte messageType) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return CommitOutcome.REJECTED;
        }
        ClaimTarget target = claimTarget(appKey, claimKeyPacketId, loginIdentity, clientMessageId);
        if (target.keys.isEmpty()) {
            return CommitOutcome.REJECTED;
        }
        List<String> args = new ArrayList<>(3 + target.ttls.size());
        args.add(ownerToken);
        args.add(String.valueOf(recordPacketId));
        args.add(payloadHash(message));
        args.add(String.valueOf(messageType));
        args.addAll(target.ttls);
        Long result = eval(redisTemplate, COMMIT_SCRIPT, target.keys, args.toArray(new String[0]));
        if (result == null) {
            return CommitOutcome.UNKNOWN;
        }
        return result == 1L ? CommitOutcome.COMMITTED : CommitOutcome.REJECTED;
    }

    /**
     * 比对删除：仅删除同 owner 同 serverPacketId 的 {@code PENDING}，绝不触碰他人占位与已提交记录。
     * {@code ownerToken} 为空时直接 no-op（宁可留 PENDING 等接管，也不误删）。
     */
    public static void releaseClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                    String loginIdentity, String clientMessageId, String ownerToken) {
        releaseClaim(redisTemplate, appKey, packetId, packetId, loginIdentity, clientMessageId, ownerToken);
    }

    /**
     * 键按 {@code claimKeyPacketId} 定位，记录内正式 ID 按 {@code recordPacketId} 比对，语义同 commit。
     */
    public static void releaseClaim(RedisTemplate<String, ?> redisTemplate, String appKey,
                                    long claimKeyPacketId, long recordPacketId,
                                    String loginIdentity, String clientMessageId, String ownerToken) {
        if (redisTemplate == null || StringUtils.isBlank(ownerToken)) {
            return;
        }
        List<String> keys = claimKeys(appKey, claimKeyPacketId, loginIdentity, clientMessageId);
        if (keys.isEmpty()) {
            return;
        }
        eval(redisTemplate, RELEASE_SCRIPT, keys, ownerToken, String.valueOf(recordPacketId));
    }

    /**
     * 正文指纹：只覆盖客户端可见、服务端落库链路不改写的字段，保证“同键不同正文”可判定，
     * 同时重发（同一正文）能对上哈希。
     */
    public static String payloadHash(Message message) {
        if (message == null) {
            return "";
        }
        Metadata metadata = message.getMetadata();
        if (metadata != null && StringUtils.isNotBlank(metadata.getHttpPushPayloadHash())) {
            // HTTP 在内容安全可能改写正文之前固定原始业务指纹；热写必须沿用同一值。
            return metadata.getHttpPushPayloadHash();
        }
        try {
            MessageDigest digestBuilder = MessageDigest.getInstance("SHA-256");
            updateDigest(digestBuilder, message.getId());
            updateDigest(digestBuilder, message.getFrom());
            updateDigest(digestBuilder, String.valueOf(message.getFromType()));
            updateDigest(digestBuilder, message.getTo());
            updateDigest(digestBuilder, String.valueOf(message.getToType()));
            updateDigest(digestBuilder, String.valueOf(message.getContentType()));
            updateDigest(digestBuilder, message.getContent());
            updateDigest(digestBuilder, message.getExtra());
            updateDigest(digestBuilder, String.valueOf(message.getQos()));
            // createTime 可能由入口服务补入，不能成为稳定 messageId 重试的冲突依据。
            updateDigest(digestBuilder, message.getCorrelationId());
            updateDigest(digestBuilder, message.getAt());
            updateDigest(digestBuilder, message.getRef());
            byte[] digest = digestBuilder.digest();
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte item : digest) {
                hex.append(String.format("%02x", item));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    /** 使用 4 字节长度前缀编码字段，避免可控分隔符和 null/空串造成边界碰撞。 */
    private static void updateDigest(MessageDigest digest, String value) {
        if (value == null) {
            updateLength(digest, -1);
            return;
        }
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        updateLength(digest, bytes.length);
        digest.update(bytes);
    }

    /** 列表先编码元素数量，再逐元素使用长度前缀，保留顺序及 null 元素语义。 */
    private static void updateDigest(MessageDigest digest, List<String> values) {
        if (values == null) {
            updateLength(digest, -1);
            return;
        }
        updateLength(digest, values.size());
        for (String value : values) {
            updateDigest(digest, value);
        }
    }

    private static void updateLength(MessageDigest digest, int value) {
        digest.update((byte) (value >>> 24));
        digest.update((byte) (value >>> 16));
        digest.update((byte) (value >>> 8));
        digest.update((byte) value);
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

    private static ClaimResult toClaimResult(List<?> raw) {
        if (raw == null || raw.isEmpty()) {
            return new ClaimResult(CLAIM_FAILED, 0L);
        }
        int state = toClaimState(toLong(raw.get(0)));
        long canonical = raw.size() > 1 ? toPacketId(raw.get(1)) : 0L;
        if (canonical < 0L) {
            canonical = 0L;
        }
        return new ClaimResult(state, canonical);
    }

    private static long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0L;
        }
    }

    /**
     * packetId 只接受字符串形态。Lua number 已经过双精度舍入，19 位 ID 不可信，
     * 此时返回 0 让调用方按“无 canonical”处理，绝不能把舍入值写回 packet。
     */
    private static long toPacketId(Object value) {
        if (value == null) {
            return 0L;
        }
        String text;
        if (value instanceof byte[] bytes) {
            text = new String(bytes, StandardCharsets.UTF_8);
        } else if (value instanceof Number) {
            log.error("QoS canonical packetId 以数值返回，可能已丢失精度，按无 canonical 处理: {}", value);
            return 0L;
        } else {
            text = String.valueOf(value);
        }
        text = text.trim();
        if (text.isEmpty()) {
            return 0L;
        }
        try {
            return Long.parseLong(text);
        } catch (NumberFormatException e) {
            log.error("QoS canonical packetId 无法解析: {}", text);
            return 0L;
        }
    }

    private static ClaimTarget claimTarget(String appKey, long packetId, String loginIdentity, String clientMessageId) {
        ClaimTarget target = new ClaimTarget();
        String pktKey = packetKey(appKey, packetId, loginIdentity);
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
        String pktKey = packetKey(appKey, packetId, loginIdentity);
        if (pktKey != null) {
            keys.add(pktKey);
        }
        String cliKey = clientKey(appKey, loginIdentity, clientMessageId);
        if (cliKey != null) {
            keys.add(cliKey);
        }
        return keys;
    }

    private static String packetKey(String appKey, long packetId, String loginIdentity) {
        if (StringUtils.isBlank(appKey) || packetId <= 0) {
            return null;
        }
        return CacheConstant.buildQosIdempotencyPacketKey(appKey, loginIdentity, packetId);
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

    @SuppressWarnings("unchecked")
    private static List<?> evalList(RedisTemplate<String, ?> template, DefaultRedisScript<List> script,
                                    List<String> keys, String... args) {
        try {
            Object raw = template.execute(script, STRING_SERIALIZER,
                    castResultSerializer(STRING_SERIALIZER), keys, (Object[]) args);
            if (raw instanceof List<?> list) {
                return list;
            }
            return null;
        } catch (Exception e) {
            log.warn("QoS 幂等脚本执行失败: {}", e.getMessage());
            return null;
        }
    }

    /** Spring 会递归使用结果序列化器解码 Lua 多返回值中的 bulk string。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisSerializer<List> castResultSerializer(RedisSerializer<String> serializer) {
        return (RedisSerializer) serializer;
    }

    private static DefaultRedisScript<Long> script(String body) {
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

    private static final class ClaimTarget {
        private final List<String> keys = new ArrayList<>(2);
        private final List<String> ttls = new ArrayList<>(2);
    }
}
