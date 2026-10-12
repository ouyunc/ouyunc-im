package com.ouyunc.repository.support;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.List;

/**
 * 咨询单未读与私聊同一套钥匙：收件人未读 Hash、已读 offset 字符串、未读 packetId ZSET。
 * 收件人是访客登录 id 或入口 id，对端是 ticketId，设备位固定。
 * 转接不换入口，所以不搬未读、不新增水位。
 */
public final class CsTicketUnreadSupport {

    private static final Logger log = LoggerFactory.getLogger(CsTicketUnreadSupport.class);

    /** 与私聊脚本同槽：三把钥匙都挂在收件人 {@code {appKey:ownerId}}。 */
    private static final Byte DEVICE = CacheConstant.CS_TICKET_OFFSET_DEVICE;

    private final StringRedisTemplate stringRedisTemplate;

    public CsTicketUnreadSupport(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * @return true 无需写或脚本已执行；false 表示 Redis 失败，调用方不得 ACK
     */
    public boolean incrOnMessage(Packet packet, CsImSessionRoute route) {
        if (packet == null || route == null || !SpecialMessageTargetValidator.isChatTargetMessage(packet)) {
            return true;
        }
        // 撤回覆盖后的正式快照仍可能进入 COMMITTED 修复，不能再次增加未读。
        if (packet.getRetain() == NumberConstant.NUMBER_1) {
            return true;
        }
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            return true;
        }
        if (!CsMessageScopeHelper.isVisitorAuthor(message, route)
                && !CsMessageScopeHelper.isStaffAuthor(message, route)) {
            return true;
        }
        String ownerId = CsMessageScopeHelper.incomingUnreadOwner(message, route);
        String appKey = message.getMetadata().getIngress().getAppKey();
        String ticketId = route.ticketId();
        if (StringUtils.isAnyBlank(appKey, ticketId, ownerId)) {
            return false;
        }
        long packetId = packet.getPacketId();
        if (packetId <= 0L) {
            return false;
        }
        return executeIncr(appKey, ownerId, ticketId.trim(), packetId);
    }

    /**
     * 推进收件人已读水位，并清掉 {@code packetId <= offset} 的未读。
     *
     * @param ownerId 访客登录 id 或入口 id
     * @return true 表示 Redis 已提交；失败返回 false，调用方不得当作已读成功
     */
    public boolean clearOnRead(String appKey, String ownerId, String ticketId, long incomingOffset, long expireTimeMs) {
        if (StringUtils.isAnyBlank(appKey, ownerId, ticketId) || incomingOffset <= 0L) {
            return false;
        }
        String tid = ticketId.trim();
        String owner = ownerId.trim();
        String field = IdentityType.CUSTOMER_SERVICE.unreadField(tid);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(
                    LuaScriptEnum.UNREAD_CLEAR_ONE2ONE_ON_READ_SCRIPT.getScript(), String.class);
            String confirmed = stringRedisTemplate.execute(script, keys(appKey, owner, tid),
                    field, String.valueOf(incomingOffset), String.valueOf(expireTimeMs));
            return confirmed != null && !confirmed.isBlank();
        } catch (Exception e) {
            log.error("clearCsTicketUnread failed appKey={} ownerId={} ticketId={} offset={}",
                    appKey, ownerId, ticketId, incomingOffset, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                    "客服 ticket 已读清未读失败: " + e.getMessage(), "CsTicketUnreadSupport", null, e);
            return false;
        }
    }

    /** 只读收件人已读水位。未写入视为 0。 */
    public long getReadOffset(String appKey, String ownerId, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ownerId, ticketId)) {
            return 0L;
        }
        String raw = stringRedisTemplate.opsForValue().get(offsetKey(appKey, ownerId.trim(), ticketId.trim()));
        if (StringUtils.isBlank(raw)) {
            return 0L;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("ticket sro 非数字 appKey={} ownerId={} ticketId={} raw={}", appKey, ownerId, ticketId, raw);
            return 0L;
        }
    }

    private boolean executeIncr(String appKey, String ownerId, String ticketId, long packetId) {
        String field = IdentityType.CUSTOMER_SERVICE.unreadField(ticketId);
        String packetIdArg = MessageContext.idGenerator().formatLongId19Str(packetId);
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        int storeMax = MessageConstant.SESSION_UNREAD_STORE_MAX;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.UNREAD_INCR_ONE2ONE_SCRIPT.getScript(), Long.class);
        try {
            stringRedisTemplate.execute(script, keys(appKey, ownerId, ticketId),
                    field, packetIdArg, "1", String.valueOf(storeMax), String.valueOf(ttl));
            return true;
        } catch (Exception e) {
            log.error("incrCsTicketUnread failed appKey={} ownerId={} ticketId={} packetId={}",
                    appKey, ownerId, ticketId, packetId, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                    "客服 ticket 未读索引更新失败: " + e.getMessage(), "CsTicketUnreadSupport", null, e);
            return false;
        }
    }

    /** ur Hash、sro 字符串、urid ZSET，槽都是收件人。 */
    private static List<String> keys(String appKey, String ownerId, String ticketId) {
        return List.of(
                CacheConstant.buildUserDeviceUnreadCacheKey(appKey, ownerId, DEVICE),
                offsetKey(appKey, ownerId, ticketId),
                CacheConstant.buildUserDeviceUnreadIdsCacheKey(appKey, ownerId, DEVICE, ticketId));
    }

    private static String offsetKey(String appKey, String ownerId, String ticketId) {
        return CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, IdentityType.CUSTOMER_SERVICE.value(), ownerId, DEVICE, ticketId);
    }
}
