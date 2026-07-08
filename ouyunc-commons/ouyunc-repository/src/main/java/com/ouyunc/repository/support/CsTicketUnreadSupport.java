package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.repository.cs.CsImSessionRoute;
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
 * 客服咨询单（ticket）维度未读 Hash。
 */
public final class CsTicketUnreadSupport {

    private static final Logger log = LoggerFactory.getLogger(CsTicketUnreadSupport.class);

    private final StringRedisTemplate stringRedisTemplate;

    public CsTicketUnreadSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public void incrOnMessage(Packet packet, CsImSessionRoute route) {
        if (packet == null || route == null || !SpecialMessageTargetValidator.isChatTargetMessage(packet)) {
            return;
        }
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        String ticketId = route.ticketId();
        String recipientId = resolveRecipientId(message, route);
        if (StringUtils.isAnyBlank(appKey, ticketId, recipientId)) {
            return;
        }
        long packetId = packet.getPacketId();
        if (packetId <= 0L) {
            return;
        }
        Collection<Byte> deviceTypes = resolveDeviceTypes(appKey, recipientId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return;
        }
        String urKey = CacheConstant.buildCsTicketUnreadHashCacheKey(appKey, ticketId.trim());
        String sroKey = CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, ticketId.trim());
        String packetIdArg = String.valueOf(packetId);
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        int storeMax = MessageConstant.SESSION_UNREAD_STORE_MAX;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.CS_TICKET_UNREAD_INCR_SCRIPT.getScript(), Long.class);
        try {
            stringRedisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                @SuppressWarnings({"unchecked", "rawtypes"})
                public Object execute(org.springframework.data.redis.core.RedisOperations operations) {
                    for (Byte deviceType : deviceTypes) {
                        String field = CacheConstant.buildCsTicketReaderDeviceField(recipientId, deviceType);
                        operations.execute(script, List.of(urKey, sroKey),
                                field, packetIdArg, "1", String.valueOf(storeMax), String.valueOf(ttl));
                    }
                    return null;
                }
            });
        } catch (Exception e) {
            log.error("incrCsTicketUnread failed appKey={} ticketId={} recipient={} packetId={}",
                    appKey, ticketId, recipientId, packetId, e);
            MessageContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(
                            ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "客服 ticket 未读索引更新失败: " + e.getMessage(),
                            packet),
                    MessageEventTypeEnum.EXCEPTION), true);
        }
    }

    public void clearOnRead(String appKey, String ticketId, String readerId, byte deviceType, long incomingOffset,
                            long expireTimeMs) {
        if (StringUtils.isAnyBlank(appKey, ticketId, readerId)) {
            return;
        }
        String tid = ticketId.trim();
        String urKey = CacheConstant.buildCsTicketUnreadHashCacheKey(appKey, tid);
        String sroKey = CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, tid);
        String field = CacheConstant.buildCsTicketReaderDeviceField(readerId, deviceType);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(
                    LuaScriptEnum.CS_TICKET_CLEAR_UNREAD_ON_READ_SCRIPT.getScript(), String.class);
            stringRedisTemplate.execute(script, List.of(urKey, sroKey),
                    field, String.valueOf(incomingOffset), String.valueOf(expireTimeMs));
        } catch (Exception e) {
            log.error("clearCsTicketUnread failed appKey={} ticketId={} reader={} offset={}",
                    appKey, ticketId, readerId, incomingOffset, e);
            MessageContext.publishEvent(new MessageEvent(
                    ExceptionEventPayload.of(
                            ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                            "客服 ticket 已读清未读失败: " + e.getMessage(),
                            null),
                    MessageEventTypeEnum.EXCEPTION), true);
        }
    }

    static String resolveRecipientId(Message message, CsImSessionRoute route) {
        if (message == null || route == null) {
            return null;
        }
        int fromType = message.getFromType();
        if (fromType == MessageFromToTypeEnum.CS_VISITOR.getType()
                || StringUtils.equals(message.getFrom(), route.userId())) {
            return route.assigneeId();
        }
        if (fromType == MessageFromToTypeEnum.CS_AGENT.getType()
                || StringUtils.equals(message.getFrom(), route.serviceIdentity())) {
            return route.userId();
        }
        return null;
    }

    private static Collection<Byte> resolveDeviceTypes(String appKey, String userId) {
        Collection<Byte> deviceTypes = MessageContext.deviceTypeList(appKey, userId);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            deviceTypes = MessageContext.deviceTypeList(appKey);
        }
        return deviceTypes;
    }
}
