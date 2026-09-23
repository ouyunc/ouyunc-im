package com.ouyunc.repository.support;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.device.DeviceTypeRegistry;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.List;

/**
 * 单聊未读：Hash 存计数，ZSET 存未读 packetId（超限只留最新），已读时只移除 {@code <= offset}。
 */
public final class UnreadIndexSupport {

    private static final Logger log = LoggerFactory.getLogger(UnreadIndexSupport.class);

    private final StringRedisTemplate stringRedisTemplate;

    public UnreadIndexSupport(RepositoryInfrastructure infra) {
        this.stringRedisTemplate = infra.stringRedisTemplate;
    }

    /**
     * 单聊/客服：他人有效聊天消息持久化成功后，对收件人各 deviceType 未读集合加入 packetId。
     */
    @SuppressWarnings("unchecked")
    public void incrOne2OneOnMessage(Packet packet) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null) {
            return;
        }
        if (!SpecialMessageTargetValidator.isChatTargetMessage(packet)) {
            return;
        }
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        String senderId = message.getFrom();
        String recipientId = message.getTo();
        if (senderId == null || recipientId == null || senderId.equals(recipientId)) {
            return;
        }
        long packetId = packet.getPacketId();
        if (packetId <= 0L) {
            log.warn("incrOne2OneOnMessage skip invalid packetId={} recipient={}", packetId, recipientId);
            return;
        }
        Collection<Byte> deviceTypes = resolveDeviceTypes(appKey, recipientId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return;
        }
        String field = IdentityType.ONE_2_ONE.unreadField(senderId);
        String packetIdArg = MessageContext.idGenerator().formatLongId19Str(packetId);
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        int storeMax = MessageConstant.SESSION_UNREAD_STORE_MAX;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.UNREAD_INCR_ONE2ONE_SCRIPT.getScript(), Long.class);

        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    for (Byte deviceType : deviceTypes) {
                        String urKey = CacheConstant.buildUserDeviceUnreadCacheKey(appKey, recipientId, deviceType);
                        String sroKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                                appKey, IdentityType.ONE_2_ONE.value(), recipientId, deviceType, senderId);
                        String uridKey = CacheConstant.buildUserDeviceUnreadIdsCacheKey(
                                appKey, recipientId, deviceType, senderId);
                        operations.execute(script, List.of(urKey, sroKey, uridKey),
                                field, packetIdArg, "1", String.valueOf(storeMax), String.valueOf(ttl));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("incrOne2OneOnMessage failed appKey={} recipient={} sender={} packetId={}",
                    appKey, recipientId, senderId, packetId, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊未读索引更新失败: " + e.getMessage(), "UnreadIndexSupport", packet, e);
        }
    }

    /**
     * 单聊本端已读或发消息静默推进：更新 sro，并按 offset 部分清除未读集合。
     *
     * @return true 表示 Redis 脚本执行成功；失败返回 false（不吞异常语义，由调用方决定是否 ACK）
     */
    @SuppressWarnings("unchecked")
    public boolean clearOne2OneOnRead(String appKey, String readerId, Byte deviceType, String peerId, long incomingOffset,
                                      long expireTimeMs) {
        if (appKey == null || readerId == null || deviceType == null || peerId == null) {
            return false;
        }
        String urKey = CacheConstant.buildUserDeviceUnreadCacheKey(appKey, readerId, deviceType);
        String sroKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, IdentityType.ONE_2_ONE.value(), readerId, deviceType, peerId);
        String uridKey = CacheConstant.buildUserDeviceUnreadIdsCacheKey(appKey, readerId, deviceType, peerId);
        String field = IdentityType.ONE_2_ONE.unreadField(peerId);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(
                    LuaScriptEnum.UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT.getScript(), String.class);
            stringRedisTemplate.execute(script, List.of(urKey, sroKey, uridKey),
                    field, String.valueOf(incomingOffset), String.valueOf(expireTimeMs));
            return true;
        } catch (Exception e) {
            log.error("clearOne2OneOnRead failed appKey={} reader={} peer={} deviceType={} offset={}",
                    appKey, readerId, peerId, deviceType, incomingOffset, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊已读 offset+未读清索引失败: " + e.getMessage(), "UnreadIndexSupport", null, e);
            return false;
        }
    }

    /**
     * 单聊撤回：收件人各 deviceType 未读集合移除 packetId，并回写 Hash 计数。
     */
    @SuppressWarnings("unchecked")
    public void removeOne2OneOnWithdraw(String appKey, String recipientId, String peerId, long packetId) {
        if (StringUtils.isAnyBlank(appKey, recipientId, peerId) || packetId <= 0L || recipientId.equals(peerId)) {
            return;
        }
        Collection<Byte> deviceTypes = resolveDeviceTypes(appKey, recipientId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return;
        }
        String field = IdentityType.ONE_2_ONE.unreadField(peerId);
        String packetIdArg = MessageContext.idGenerator().formatLongId19Str(packetId);
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.UNREAD_REMOVE_ONE2ONE_ON_WITHDRAW_SCRIPT.getScript(), Long.class);
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    for (Byte deviceType : deviceTypes) {
                        String urKey = CacheConstant.buildUserDeviceUnreadCacheKey(appKey, recipientId, deviceType);
                        String uridKey = CacheConstant.buildUserDeviceUnreadIdsCacheKey(
                                appKey, recipientId, deviceType, peerId);
                        operations.execute(script, List.of(urKey, uridKey),
                                field, packetIdArg, String.valueOf(ttl));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("removeOne2OneOnWithdraw failed appKey={} recipient={} peer={} packetId={}",
                    appKey, recipientId, peerId, packetId, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR, "单聊撤回清未读失败: " + e.getMessage(), "UnreadIndexSupport", null, e);
        }
    }

    private static Collection<Byte> resolveDeviceTypes(String appKey, String userId) {
        Collection<Byte> deviceTypes = DeviceTypeRegistry.list(appKey, userId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            deviceTypes = DeviceTypeRegistry.list(appKey);
        }
        return deviceTypes;
    }
}
