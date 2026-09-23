package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageFromToTypeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.TimeUtil;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 撤回消息加载与 Redis 副作用。
 */
public final class WithdrawMessageSupport {

    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageSupport.class);

    private final SpecialMessageLoader specialMessageLoader;
    private final RedisTemplate redisTemplate;
    private final SessionIndexSupport sessionIndexSupport;
    private final UnreadIndexSupport unreadIndexSupport;
    private final CsTicketUnreadSupport csTicketUnreadSupport;

    public WithdrawMessageSupport(SpecialMessageLoader specialMessageLoader, RedisTemplate redisTemplate,
                                  SessionIndexSupport sessionIndexSupport,
                                  UnreadIndexSupport unreadIndexSupport,
                                  CsTicketUnreadSupport csTicketUnreadSupport) {
        this.specialMessageLoader = specialMessageLoader;
        this.redisTemplate = redisTemplate;
        this.sessionIndexSupport = sessionIndexSupport;
        this.unreadIndexSupport = unreadIndexSupport;
        this.csTicketUnreadSupport = csTicketUnreadSupport;
    }

    public Mono<List<Packet>> reactiveLoadWithdrawTargetPackets(Packet packet, String scopeId,
                                                                MessageIndexScope scope, boolean isValidSender) {
        return specialMessageLoader.reactiveLoadValidatedSpecialPackets(
                packet, scopeId, scope, MessageConstant.MAX_WITHDRAW_MESSAGE_COUNT,
                (specialPackets) -> {
                    if (isValidSender) {
                        for (Packet specialPacket : specialPackets) {
                            if (specialPacket == null || specialPacket.getMessage() == null
                                    || !specialPacket.getMessage().getFrom().equals(packet.getMessage().getFrom())) {
                                log.error("消息: {} 对应的消息不属于发送者！", packet);
                                return Mono.just(false);
                            }
                        }
                    }
                    return Mono.just(true);
                }, packets -> isWithdrawTargetPacketsValid(packets, isValidSender));
    }

    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveWithdrawMessage(Packet packet, String scopeId, MessageIndexScope scope,
                                                 List<Packet> targetPackets) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || StringUtils.isBlank(scopeId) || CollectionUtils.isEmpty(targetPackets)) {
            log.error("撤回 Redis 更新参数非法 | packet={}, scopeId={}, targetSize={}",
                    packet, scopeId, targetPackets == null ? null : targetPackets.size());
            return Mono.just(false);
        }
        String appKey = packet.getMessage().getMetadata().getAppKey();
        return Mono.fromCallable(() -> {
                    applyWithdrawnPacketsToRedis(appKey, scopeId, scope, targetPackets);
                    return Boolean.TRUE;
                })
                .doOnError(e -> log.error("撤回 Redis 更新失败 | appKey={}, scopeId={}", appKey, scopeId, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private boolean isWithdrawTargetPacketsValid(List<Packet> packets, boolean enforceTimeWindow) {
        for (Packet targetPacket : packets) {
            if (!SpecialMessageTargetValidator.isChatTargetMessage(targetPacket)) {
                log.error("撤回目标消息内容类型不允许撤回 | packetId={}", targetPacket == null ? null : targetPacket.getPacketId());
                return false;
            }
            if (targetPacket.getRetain() == NumberConstant.NUMBER_1) {
                log.error("撤回目标消息已被撤回 | packetId={}", targetPacket.getPacketId());
                return false;
            }
            if (enforceTimeWindow && !isWithinWithdrawTimeWindow(targetPacket)) {
                log.error("撤回目标消息已超过允许撤回时间窗口 | packetId={}", targetPacket.getPacketId());
                return false;
            }
        }
        return true;
    }

    private static boolean isWithinWithdrawTimeWindow(Packet targetPacket) {
        if (targetPacket == null || targetPacket.getMessage() == null) {
            return false;
        }
        Message message = targetPacket.getMessage();
        long sendTime = 0L;
        if (message.getMetadata() != null) {
            sendTime = message.getMetadata().getServerTime();
        }
        if (sendTime <= 0L) {
            sendTime = message.getCreateTime();
        }
        if (sendTime <= 0L) {
            return false;
        }
        return TimeUtil.currentTimeMillis() - sendTime <= MessageConstant.WITHDRAW_MESSAGE_TIME_WINDOW_MS;
    }

    @SuppressWarnings("unchecked")
    private void applyWithdrawnPacketsToRedis(String appKey, String scopeId, MessageIndexScope scope,
                                              List<Packet> packets) {
        String sessionCacheKey = resolveMessageIndexKey(appKey, scopeId, scope);
        List<String> indexMembers = new ArrayList<>(packets.size());
        // 正文 Packet 走 Jackson 模板；会话 ZSet 成员是原始字符串，必须用 StringRedisTemplate 删除
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (Packet withdrawPacket : packets) {
                    withdrawPacket.setRetain(NumberConstant.NUMBER_1);
                    operations.opsForValue().set((K) CacheConstant.buildMessageCacheKey(appKey, withdrawPacket.getPacketId()),
                            (V) withdrawPacket, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    indexMembers.add(MessageContext.idGenerator().formatLongId19Str(withdrawPacket.getPacketId()));
                }
                return null;
            }
        });
        sessionIndexSupport.removeMembers(sessionCacheKey, indexMembers);
        // 撤回后从未读 SET/Hash 摘掉 packetId，避免单聊/客服未读虚高
        clearUnreadForWithdrawnPackets(appKey, scopeId, scope, packets);
    }

    /**
     * 单聊：仅当 scopeId 为双方 peer session 时清收件人未读；客服 ticket：清消息 to 侧未读。
     */
    private void clearUnreadForWithdrawnPackets(String appKey, String scopeId, MessageIndexScope scope,
                                                List<Packet> packets) {
        for (Packet withdrawPacket : packets) {
            if (withdrawPacket == null || withdrawPacket.getMessage() == null) {
                continue;
            }
            Message message = withdrawPacket.getMessage();
            long packetId = withdrawPacket.getPacketId();
            if (packetId <= 0L) {
                continue;
            }
            if (scope == MessageIndexScope.CS_TICKET) {
                String recipientId = resolveCsWithdrawRecipient(message);
                if (StringUtils.isNotBlank(recipientId)) {
                    csTicketUnreadSupport.removeOnWithdraw(appKey, scopeId, recipientId, packetId);
                }
                continue;
            }
            if (scope != MessageIndexScope.CHANNEL_SESSION) {
                continue;
            }
            String from = message.getFrom();
            String to = message.getTo();
            if (StringUtils.isAnyBlank(from, to) || from.equals(to)) {
                continue;
            }
            // 群聊 sessionId≠peerSession，跳过；单聊才清收件人 urid
            if (!StringUtils.equals(scopeId, IdentityUtil.sessionId(from, to))) {
                continue;
            }
            unreadIndexSupport.removeOne2OneOnWithdraw(appKey, to, from, packetId);
        }
    }

    /**
     * 客服撤回目标消息的收件人：优先 to；访客/座席 fromType 兜底。
     */
    private static String resolveCsWithdrawRecipient(Message message) {
        if (message == null) {
            return null;
        }
        if (StringUtils.isNotBlank(message.getTo())) {
            return message.getTo();
        }
        int fromType = message.getFromType();
        if (fromType == MessageFromToTypeEnum.CS_VISITOR.getType()
                || fromType == MessageFromToTypeEnum.CS_AGENT.getType()) {
            return message.getTo();
        }
        return null;
    }

    static String resolveMessageIndexKey(String appKey, String scopeId, MessageIndexScope scope) {
        if (scope == MessageIndexScope.CS_TICKET) {
            return CacheConstant.buildCsTicketMessageSessionCacheKey(appKey, scopeId);
        }
        return CacheConstant.buildSessionCacheKey(appKey, scopeId);
    }
}
