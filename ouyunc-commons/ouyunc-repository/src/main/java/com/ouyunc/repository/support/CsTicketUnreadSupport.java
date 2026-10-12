package com.ouyunc.repository.support;

import com.ouyunc.core.exception.ExceptionReporter;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
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
 * 咨询单未读只有两份：staff（访客消息，任一坐席/机器人已读即推进）、visitor（坐席消息）。
 * 不按坐席、不按设备拆。转接不搬这两份。
 */
public final class CsTicketUnreadSupport {

    private static final Logger log = LoggerFactory.getLogger(CsTicketUnreadSupport.class);

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
        String side = CsMessageScopeHelper.incomingUnreadSide(message, route);
        String appKey = message.getMetadata().getIngress().getAppKey();
        String ticketId = route.ticketId();
        if (StringUtils.isAnyBlank(appKey, ticketId, side)) {
            return true;
        }
        long packetId = packet.getPacketId();
        if (packetId <= 0L) {
            return false;
        }
        return executeIncr(appKey, ticketId.trim(), side, packetId);
    }

    /**
     * @param side {@link CacheConstant#CS_TICKET_UNREAD_STAFF} 或 {@link CacheConstant#CS_TICKET_UNREAD_VISITOR}
     * @return true 表示 Redis 已提交；失败返回 false，调用方不得当作已读成功
     */
    public boolean clearOnRead(String appKey, String ticketId, String side, long incomingOffset, long expireTimeMs) {
        if (StringUtils.isAnyBlank(appKey, ticketId, side) || incomingOffset <= 0L) {
            return false;
        }
        String tid = ticketId.trim();
        String urKey = CacheConstant.buildCsTicketUnreadHashCacheKey(appKey, tid);
        String sroKey = CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, tid);
        String uridKey = CacheConstant.buildCsTicketUnreadIdsCacheKey(appKey, tid, side);
        try {
            DefaultRedisScript<String> script = new DefaultRedisScript<>(
                    LuaScriptEnum.CS_TICKET_CLEAR_UNREAD_ON_READ_SCRIPT.getScript(), String.class);
            String confirmed = stringRedisTemplate.execute(script, List.of(urKey, sroKey, uridKey),
                    side, String.valueOf(incomingOffset), String.valueOf(expireTimeMs));
            return confirmed != null && !confirmed.isBlank();
        } catch (Exception e) {
            log.error("clearCsTicketUnread failed appKey={} ticketId={} side={} offset={}",
                    appKey, ticketId, side, incomingOffset, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                    "客服 ticket 已读清未读失败: " + e.getMessage(), "CsTicketUnreadSupport", null, e);
            return false;
        }
    }

    private boolean executeIncr(String appKey, String ticketId, String side, long packetId) {
        String urKey = CacheConstant.buildCsTicketUnreadHashCacheKey(appKey, ticketId);
        String sroKey = CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, ticketId);
        String uridKey = CacheConstant.buildCsTicketUnreadIdsCacheKey(appKey, ticketId, side);
        String packetIdArg = MessageContext.idGenerator().formatLongId19Str(packetId);
        long ttl = MessageConstant.CACHE_USER_DEVICE_UNREAD_EXPIRE_TIMESTAMP;
        int storeMax = MessageConstant.SESSION_UNREAD_STORE_MAX;
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.CS_TICKET_UNREAD_INCR_SCRIPT.getScript(), Long.class);
        try {
            stringRedisTemplate.execute(script, List.of(urKey, sroKey, uridKey),
                    side, packetIdArg, "1", String.valueOf(storeMax), String.valueOf(ttl));
            return true;
        } catch (Exception e) {
            log.error("incrCsTicketUnread failed appKey={} ticketId={} side={} packetId={}",
                    appKey, ticketId, side, packetId, e);
            ExceptionReporter.reportSystem(ExceptionCodeEnum.CACHE_PERSISTENCE_ERROR,
                    "客服 ticket 未读索引更新失败: " + e.getMessage(), "CsTicketUnreadSupport", null, e);
            return false;
        }
    }
}
