package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.packet.Packet;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

/**
 * 单聊 / 群聊 session 维度「最后一条有效聊天消息」Redis 读写与撤回回退。
 * <p>普通发送走 max-merge；撤回回退走 CAS，避免旧指针覆盖并发写入的更新指针。</p>
 */
public final class SessionLastMessageSupport {

    private static final Logger log = LoggerFactory.getLogger(SessionLastMessageSupport.class);

    private static final int REFRESH_SCAN_LIMIT = 25;

    private final StringRedisTemplate stringRedisTemplate;
    private final MessagePacketQuerySupport messagePacketQuery;

    public SessionLastMessageSupport(StringRedisTemplate stringRedisTemplate,
                                     MessagePacketQuerySupport messagePacketQuery,
                                     SessionMessagePersistenceSupport sessionPersistence) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.messagePacketQuery = messagePacketQuery;
        // sessionPersistence 保留构造兼容；lm 写入已统一走 max-merge / CAS
    }

    /**
     * 撤回后：若 session lm 指向被撤回消息或已无效，则 CAS 回退到 session msgs ZSet 上一条可见聊天。
     */
    public void refreshAfterWithdraw(String appKey, String sessionId) {
        if (StringUtils.isAnyBlank(appKey, sessionId)) {
            return;
        }
        String sid = sessionId.trim();
        Long currentLm = getLastPacketId(appKey, sid);
        if (currentLm == null) {
            return;
        }
        if (!isCurrentSessionLmStillValid(appKey, sid, currentLm)) {
            recomputeAndCasReplace(appKey, sid, currentLm);
        }
    }

    public Long getLastPacketId(String appKey, String sessionId) {
        if (StringUtils.isAnyBlank(appKey, sessionId)) {
            return null;
        }
        String raw = stringRedisTemplate.opsForValue().get(
                CacheConstant.buildSessionLastMessageCacheKey(appKey, sessionId.trim()));
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            return Long.parseLong(raw.trim());
        } catch (NumberFormatException ex) {
            log.warn("session lm 非数字 packetId appKey={} sessionId={} raw={}", appKey, sessionId, raw);
            return null;
        }
    }

    private boolean isCurrentSessionLmStillValid(String appKey, String sessionId, long currentLm) {
        List<Packet> current = messagePacketQuery.getPackets(appKey, List.of(currentLm));
        if (CollectionUtils.isEmpty(current) || current.getFirst() == null) {
            return false;
        }
        Packet packet = current.getFirst();
        return CsTicketLastMessageSupport.isCountableChatMessage(packet)
                && SpecialMessageLoader.belongsToScope(packet, sessionId, MessageIndexScope.CHANNEL_SESSION);
    }

    private void recomputeAndCasReplace(String appKey, String sessionId, long expectedCurrent) {
        List<Packet> recent = loadRecentSessionPackets(appKey, sessionId, REFRESH_SCAN_LIMIT);
        Packet fallback = null;
        if (CollectionUtils.isNotEmpty(recent)) {
            fallback = recent.stream()
                    .filter(Objects::nonNull)
                    .filter(CsTicketLastMessageSupport::isCountableChatMessage)
                    .filter(p -> SpecialMessageLoader.belongsToScope(p, sessionId, MessageIndexScope.CHANNEL_SESSION))
                    .max(Comparator.comparingLong(Packet::getPacketId))
                    .orElse(null);
        }
        String newId = fallback == null ? "" : String.valueOf(fallback.getPacketId());
        boolean replaced = casReplace(appKey, sessionId, expectedCurrent, newId);
        if (!replaced) {
            log.debug("session lm CAS 未命中（指针已被更新） appKey={} sessionId={} expected={}",
                    appKey, sessionId, expectedCurrent);
        }
    }

    private boolean casReplace(String appKey, String sessionId, long expectedCurrent, String newPacketIdOrEmpty) {
        String lmKey = CacheConstant.buildSessionLastMessageCacheKey(appKey, sessionId.trim());
        DefaultRedisScript<Long> script = new DefaultRedisScript<>(
                LuaScriptEnum.LM_CAS_REPLACE_SCRIPT.getScript(), Long.class);
        Long result = stringRedisTemplate.execute(script, List.of(lmKey),
                String.valueOf(expectedCurrent),
                newPacketIdOrEmpty == null ? "" : newPacketIdOrEmpty,
                String.valueOf(MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP));
        return result != null && result == 1L;
    }

    private List<Packet> loadRecentSessionPackets(String appKey, String sessionId, int limit) {
        String sessionKey = CacheConstant.buildSessionCacheKey(appKey, sessionId);
        var tuples = stringRedisTemplate.opsForZSet().reverseRangeWithScores(sessionKey, 0, limit - 1L);
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
}
