package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.constants.IdentityType;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collection;
import java.util.List;

/**
 * 单聊未读 Hash（ur）写路径：按用户×设备增量维护；群聊不在此维护。
 */
public final class UnreadIndexSupport {

    private static final Logger log = LoggerFactory.getLogger(UnreadIndexSupport.class);

    private final RedisTemplate redisTemplate;

    public UnreadIndexSupport(RepositoryInfrastructure infra) {
        this.redisTemplate = infra.redisTemplate;
    }

    /**
     * 单聊/客服：他人有效聊天消息持久化成功后，对收件人各 deviceType 未读 +1。
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
        Collection<Byte> deviceTypes = resolveDeviceTypes(appKey, recipientId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return;
        }
        String field = IdentityType.ONE_2_ONE.unreadField(senderId);
        String packetIdArg = String.valueOf(packet.getPacketId());
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        int storeMax = MessageConstant.SESSION_UNREAD_STORE_MAX;

        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.UNREAD_INCR_ONE2ONE_SCRIPT.getScript(), Long.class);

        try {
            for (Byte deviceType : deviceTypes) {
                String urKey = CacheConstant.buildUserDeviceUnreadCacheKey(appKey, recipientId, deviceType);
                String sroKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                        appKey, IdentityType.ONE_2_ONE.value(), recipientId, deviceType, senderId);
                redisTemplate.execute(script, List.of(urKey, sroKey),
                        field, packetIdArg, "1", String.valueOf(storeMax), String.valueOf(ttl));
            }
        } catch (Exception e) {
            log.warn("incrOne2OneOnMessage failed appKey={} recipient={} sender={}",
                    appKey, recipientId, senderId, e);
        }
    }

    /**
     * 单聊本端已读或发消息静默推进：更新 sro 并 HDEL 未读 field。
     */
    @SuppressWarnings("unchecked")
    public void clearOne2OneOnRead(String appKey, String readerId, Byte deviceType, String peerId, long incomingOffset,
                                   long expireTimeMs) {
        if (appKey == null || readerId == null || deviceType == null || peerId == null) {
            return;
        }
        String urKey = CacheConstant.buildUserDeviceUnreadCacheKey(appKey, readerId, deviceType);
        String sroKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, IdentityType.ONE_2_ONE.value(), readerId, deviceType, peerId);
        String field = IdentityType.ONE_2_ONE.unreadField(peerId);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(
                    LuaScriptEnum.UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT.getScript(), String.class);
            redisTemplate.execute(script, List.of(urKey, sroKey),
                    field, String.valueOf(incomingOffset), String.valueOf(expireTimeMs));
        } catch (Exception e) {
            log.warn("clearOne2OneOnRead failed reader={} peer={} deviceType={}", readerId, peerId, deviceType, e);
        }
    }

    private static Collection<Byte> resolveDeviceTypes(String appKey, String userId) {
        Collection<Byte> deviceTypes = MessageContext.deviceTypeList(appKey, userId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            deviceTypes = MessageContext.deviceTypeList(appKey);
        }
        return deviceTypes;
    }
}
