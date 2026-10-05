package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageDeliveryChannelEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * 把“消息已提交”和“首次扇出已完成”分开。
 * <p>
 * 外渠任务按 appKey + canonical packetId + 收件人 + 渠道记录。
 * 值 {@code P} 表示已建立但 broker 未确认，{@code C} 表示该收件人已确认。
 * 完成记录只升级为 C，不按 messageId 整键删除，避免同租户另一个发送者清掉任务。
 */
public final class DeliveryCompletionSupport {

    private static final Logger log = LoggerFactory.getLogger(DeliveryCompletionSupport.class);

    /** 待 broker 确认。 */
    public static final String TASK_PENDING = "P";

    /** 该收件人渠道已确认，重入不得再发布。 */
    public static final String TASK_CONFIRMED = "C";

    /**
     * 原子获取首次扇出执行权。返回 2 表示已完成，1 表示本次获取成功，0 表示其他 owner 正在执行。
     * done/run 使用同一 canonical packetId 哈希标签，可在 Redis Cluster 中执行双 key Lua。
     */
    private static final DefaultRedisScript<Long> START_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('EXISTS', KEYS[1]) == 1 then return 2 end
            if redis.call('SET', KEYS[2], ARGV[1], 'NX', 'PX', ARGV[2]) then return 1 end
            if redis.call('EXISTS', KEYS[1]) == 1 then return 2 end
            return 0
            """, Long.class);

    /** 仅当前 owner 可以写完成标记并释放执行锁。 */
    private static final DefaultRedisScript<Long> FINISH_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[2]) ~= ARGV[1] then return 0 end
            redis.call('PSETEX', KEYS[1], ARGV[2], '1')
            redis.call('DEL', KEYS[2])
            return 1
            """, Long.class);

    /** 仅当前 owner 可以释放执行锁，避免旧执行者误删接管者的新锁。 */
    private static final DefaultRedisScript<Long> ABORT_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('DEL', KEYS[1])
            """, Long.class);

    /** 长时间群扇出在批次边界续租；只有当前 owner 可以延长执行权。 */
    private static final DefaultRedisScript<Long> RENEW_SCRIPT = new DefaultRedisScript<>("""
            if redis.call('GET', KEYS[1]) ~= ARGV[1] then return 0 end
            return redis.call('PEXPIRE', KEYS[1], ARGV[2])
            """, Long.class);

    /**
     * 外渠任务只允许空/P → P，已确认的 C 不得回退。
     * HGET 与 HSET 必须在同一脚本内，避免租约过期后旧执行者覆盖新确认。
     * 返回 1 表示已写入 PENDING，0 表示已经是 CONFIRMED。
     */
    private static final DefaultRedisScript<Long> MARK_PENDING_SCRIPT = new DefaultRedisScript<>("""
            local current = redis.call('HGET', KEYS[1], ARGV[1])
            if current == ARGV[3] then return 0 end
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[4])
            return 1
            """, Long.class);

    /** 确认只升级为 C，并与 TTL 同一脚本提交，避免只写状态、过期失败。 */
    private static final DefaultRedisScript<Long> CONFIRM_SCRIPT = new DefaultRedisScript<>("""
            redis.call('HSET', KEYS[1], ARGV[1], ARGV[2])
            redis.call('PEXPIRE', KEYS[1], ARGV[3])
            return 1
            """, Long.class);

    public enum RunState {
        /** 已经完成首次扇出。 */
        DONE,
        /** 另一个请求正在扇出。 */
        BUSY,
        /** 本次拿到 owner。 */
        ACQUIRED
    }

    private final StringRedisTemplate redis;

    public DeliveryCompletionSupport(StringRedisTemplate redis) {
        this.redis = redis;
    }

    public boolean isDeliveryFinished(Packet packet) {
        String key = doneKey(packet);
        return key != null && Boolean.TRUE.equals(redis.hasKey(key));
    }

    public boolean isRequestCommandConfirmed(Packet packet) {
        Identity identity = identity(packet);
        return identity != null && Boolean.TRUE.equals(redis.hasKey(
                CacheConstant.buildRequestCommandConfirmedKey(identity.appKey, identity.packetId)));
    }

    /** 写入单调确认标记；写失败必须传播，不能把未知确认误判为已完成。 */
    public void confirmRequestCommand(Packet packet) {
        Identity identity = identity(packet);
        if (identity == null) {
            throw new IllegalArgumentException("请求领域命令缺少持久化身份");
        }
        redis.opsForValue().set(CacheConstant.buildRequestCommandConfirmedKey(identity.appKey, identity.packetId),
                TASK_CONFIRMED, java.time.Duration.ofMillis(MessageContext.messageHotDataTtlMillis()));
    }

    /**
     * 只有一个 owner 可以执行首次扇出。已完成直接返回 {@link RunState#DONE}。
     */
    public RunState tryStartDelivery(Packet packet, String ownerToken) {
        if (StringUtils.isBlank(ownerToken)) {
            return RunState.BUSY;
        }
        String done = doneKey(packet);
        String run = runKey(packet);
        if (done == null || run == null) {
            return RunState.BUSY;
        }
        Long result = redis.execute(START_SCRIPT, java.util.List.of(done, run), ownerToken,
                String.valueOf(MessageConstant.DELIVERY_RUN_LOCK_MILLIS));
        if (Long.valueOf(2L).equals(result)) {
            return RunState.DONE;
        }
        return Long.valueOf(1L).equals(result) ? RunState.ACQUIRED : RunState.BUSY;
    }

    public boolean finishDelivery(Packet packet, String ownerToken) {
        String done = doneKey(packet);
        String run = runKey(packet);
        if (done == null || run == null || StringUtils.isBlank(ownerToken)) {
            return false;
        }
        Long result = redis.execute(FINISH_SCRIPT, java.util.List.of(done, run), ownerToken,
                String.valueOf(MessageContext.messageHotDataTtlMillis()));
        return Long.valueOf(1L).equals(result);
    }

    public boolean abortDelivery(Packet packet, String ownerToken) {
        String run = runKey(packet);
        if (run == null || StringUtils.isBlank(ownerToken)) {
            return false;
        }
        Long result = redis.execute(ABORT_SCRIPT, java.util.List.of(run), ownerToken);
        return Long.valueOf(1L).equals(result);
    }

    public boolean renewDelivery(Packet packet, String ownerToken) {
        String run = runKey(packet);
        if (run == null || StringUtils.isBlank(ownerToken)) {
            return false;
        }
        Long result = redis.execute(RENEW_SCRIPT, java.util.List.of(run), ownerToken,
                String.valueOf(MessageConstant.DELIVERY_RUN_LOCK_MILLIS));
        return Long.valueOf(1L).equals(result);
    }

    /**
     * 发布前写入。已经确认的收件人返回 false，调用方不得再次发布。
     */
    public boolean markExternalPending(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        String key = taskKey(packet);
        String field = taskField(recipientId, channel);
        if (key == null || field == null) {
            return false;
        }
        Long written = redis.execute(MARK_PENDING_SCRIPT, java.util.List.of(key),
                field, TASK_PENDING, TASK_CONFIRMED,
                String.valueOf(MessageContext.messageHotDataTtlMillis()));
        return Long.valueOf(1L).equals(written);
    }

    public boolean isExternalConfirmed(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        String key = taskKey(packet);
        String field = taskField(recipientId, channel);
        if (key == null || field == null) {
            return false;
        }
        return TASK_CONFIRMED.equals(redis.opsForHash().get(key, field));
    }

    public void confirmExternal(Packet packet, String recipientId, MessageDeliveryChannelEnum channel) {
        String key = taskKey(packet);
        String field = taskField(recipientId, channel);
        if (key == null || field == null) {
            log.warn("外渠完成记录缺少身份, recipientId={}", recipientId);
            return;
        }
        redis.execute(CONFIRM_SCRIPT, java.util.List.of(key),
                field, TASK_CONFIRMED, String.valueOf(MessageContext.messageHotDataTtlMillis()));
    }

    private static String doneKey(Packet packet) {
        Identity identity = identity(packet);
        return identity == null ? null
                : CacheConstant.buildDeliveryDoneKey(identity.appKey, identity.packetId);
    }

    private static String runKey(Packet packet) {
        Identity identity = identity(packet);
        return identity == null ? null
                : CacheConstant.buildDeliveryRunKey(identity.appKey, identity.packetId);
    }

    private static String taskKey(Packet packet) {
        Identity identity = identity(packet);
        return identity == null ? null
                : CacheConstant.buildExternalDeliveryTaskKey(identity.appKey, identity.packetId);
    }

    private static String taskField(String recipientId, MessageDeliveryChannelEnum channel) {
        if (channel == null || !channel.isExternalMessaging()) {
            return null;
        }
        return CacheConstant.externalDeliveryTaskField(recipientId, channel.getKey());
    }

    private static Identity identity(Packet packet) {
        if (packet == null || packet.getPacketId() <= 0L || packet.getMessage() == null) {
            return null;
        }
        Message message = packet.getMessage();
        if (message.getMetadata() == null || message.getMetadata().getIngress() == null) {
            return null;
        }
        String appKey = message.getMetadata().getIngress().getAppKey();
        if (StringUtils.isBlank(appKey)) {
            return null;
        }
        return new Identity(appKey, packet.getPacketId());
    }

    private record Identity(String appKey, long packetId) {
    }
}
