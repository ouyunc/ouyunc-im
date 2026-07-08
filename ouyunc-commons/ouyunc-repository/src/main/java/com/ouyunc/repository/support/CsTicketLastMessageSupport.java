package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 客服咨询单（ticket）维度「最后一条有效聊天消息」Redis 读写。
 */
public final class CsTicketLastMessageSupport {

    private static final Logger log = LoggerFactory.getLogger(CsTicketLastMessageSupport.class);

    private static final int REFRESH_SCAN_LIMIT = 25;

    private final StringRedisTemplate stringRedisTemplate;
    private final MessagePacketQuerySupport messagePacketQuery;

    public CsTicketLastMessageSupport(StringRedisTemplate stringRedisTemplate, MessagePacketQuerySupport messagePacketQuery) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messagePacketQuery = messagePacketQuery;
    }

    public void save(String ticketId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        if (StringUtils.isBlank(ticketId) || lastPacket == null || lastPacket.getMessage() == null) {
            return;
        }
        Message message = lastPacket.getMessage();
        if (message.getMetadata() == null || StringUtils.isBlank(message.getMetadata().getAppKey())) {
            return;
        }
        if (!isCountableChatMessage(lastPacket)) {
            return;
        }
        String appKey = message.getMetadata().getAppKey();
        String lmKey = CacheConstant.buildCsTicketLastMessageCacheKey(appKey, ticketId.trim());
        long ttlMs = timeUnit.toMillis(expireTime);
        DefaultRedisScript<String> script = new DefaultRedisScript<>(
                LuaScriptEnum.CS_TICKET_LM_MAX_SCRIPT.getScript(), String.class);
        stringRedisTemplate.execute(script, List.of(lmKey),
                String.valueOf(lastPacket.getPacketId()), String.valueOf(ttlMs));
    }

    public Long getLastPacketId(String appKey, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ticketId)) {
            return null;
        }
        String raw = stringRedisTemplate.opsForValue().get(
                CacheConstant.buildCsTicketLastMessageCacheKey(appKey, ticketId.trim()));
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("ticket lm 非数字 packetId appKey={} ticketId={} raw={}", appKey, ticketId, raw);
            return null;
        }
    }

    public void delete(String appKey, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ticketId)) {
            return;
        }
        stringRedisTemplate.delete(CacheConstant.buildCsTicketLastMessageCacheKey(appKey, ticketId.trim()));
    }

    public void deleteAllTicketImState(String appKey, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ticketId)) {
            return;
        }
        String tid = ticketId.trim();
        stringRedisTemplate.delete(List.of(
                CacheConstant.buildCsTicketLastMessageCacheKey(appKey, tid),
                CacheConstant.buildCsTicketMessageSessionCacheKey(appKey, tid),
                CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, tid),
                CacheConstant.buildCsTicketUnreadHashCacheKey(appKey, tid)));
    }

    /**
     * 撤回后：若 ticket lm 指向被撤回消息或已无效，则回退到 ticket msgs ZSet 上一条可见聊天。
     */
    public void refreshAfterWithdraw(String appKey, String ticketId) {
        if (StringUtils.isAnyBlank(appKey, ticketId)) {
            return;
        }
        String tid = ticketId.trim();
        Long currentLm = getLastPacketId(appKey, tid);
        if (currentLm == null) {
            return;
        }
        if (!isCurrentTicketLmStillValid(appKey, tid, currentLm)) {
            recomputeAndSaveTicketLm(appKey, tid);
        }
    }

    private boolean isCurrentTicketLmStillValid(String appKey, String ticketId, long currentLm) {
        List<Packet> current = messagePacketQuery.getPackets(appKey, List.of(currentLm));
        if (CollectionUtils.isEmpty(current) || current.getFirst() == null) {
            return false;
        }
        Packet packet = current.getFirst();
        return isCountableChatMessage(packet) && belongsToTicketStrict(packet, ticketId);
    }

    private void recomputeAndSaveTicketLm(String appKey, String ticketId) {
        List<Packet> recent = loadRecentTicketPackets(appKey, ticketId, REFRESH_SCAN_LIMIT);
        if (CollectionUtils.isEmpty(recent)) {
            delete(appKey, ticketId);
            return;
        }
        Packet fallback = recent.stream()
                .filter(Objects::nonNull)
                .filter(CsTicketLastMessageSupport::isCountableChatMessage)
                .max(Comparator.comparingLong(Packet::getPacketId))
                .orElse(null);
        if (fallback == null) {
            delete(appKey, ticketId);
            return;
        }
        save(ticketId, fallback, MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
    }

    @SuppressWarnings("unchecked")
    private List<Packet> loadRecentTicketPackets(String appKey, String ticketId, int limit) {
        String ticketKey = CacheConstant.buildCsTicketMessageSessionCacheKey(appKey, ticketId);
        var tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(ticketKey, 0, limit - 1L);
        if (tuples == null || tuples.isEmpty()) {
            return List.of();
        }
        List<Long> ids = tuples.stream()
                .map(t -> parseZSetMemberPacketId(t.getValue()))
                .filter(Objects::nonNull)
                .toList();
        if (ids.isEmpty()) {
            return List.of();
        }
        return messagePacketQuery.getPackets(appKey, ids);
    }

    private static Long parseZSetMemberPacketId(String member) {
        if (StringUtils.isBlank(member)) {
            return null;
        }
        try {
            long id = Long.parseLong(member.trim());
            return id > 0L ? id : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    static boolean isCountableChatMessage(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        if (packet.getRetain() == NumberConstant.NUMBER_1) {
            return false;
        }
        int contentType = packet.getMessage().getContentType();
        return contentType != MessageContentTypeEnum.WITHDRAW_CONTENT.getType()
                && contentType != MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType();
    }

    static boolean belongsToTicketStrict(Packet packet, String ticketId) {
        if (packet == null || packet.getMessage() == null || StringUtils.isBlank(ticketId)) {
            return false;
        }
        return StringUtils.equals(ticketId.trim(), packet.getMessage().getCorrelationId());
    }

    public static boolean isVisitorSide(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        int fromType = packet.getMessage().getFromType();
        return fromType == MessageFromToTypeEnum.CS_VISITOR.getType();
    }
}
