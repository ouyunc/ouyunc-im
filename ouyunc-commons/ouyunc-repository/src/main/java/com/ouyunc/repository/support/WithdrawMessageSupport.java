package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.packet.Packet;
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

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 撤回消息加载与 Redis 副作用。
 */
public final class WithdrawMessageSupport {

    private static final Logger log = LoggerFactory.getLogger(WithdrawMessageSupport.class);

    private final SpecialMessageLoader specialMessageLoader;
    private final RedisTemplate redisTemplate;

    public WithdrawMessageSupport(SpecialMessageLoader specialMessageLoader, RedisTemplate redisTemplate) {
        this.specialMessageLoader = specialMessageLoader;
        this.redisTemplate = redisTemplate;
    }

    public Mono<List<Packet>> reactiveLoadWithdrawTargetPackets(Packet packet, String sessionId, boolean isValidSender) {
        return specialMessageLoader.reactiveLoadValidatedSpecialPackets(packet, sessionId, (specialPackets) -> {
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
        }, packets -> {
            if (!isWithdrawTargetPacketsValid(packets)) {
                log.error("撤回目标消息内容类型错误，不允许撤回撤回消息或已读消息");
                return false;
            }
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveWithdrawMessage(Packet packet, String sessionId, List<Packet> targetPackets) {
        if (packet == null || packet.getMessage() == null || packet.getMessage().getMetadata() == null
                || StringUtils.isBlank(sessionId) || CollectionUtils.isEmpty(targetPackets)) {
            log.error("撤回 Redis 更新参数非法 | packet={}, sessionId={}, targetSize={}",
                    packet, sessionId, targetPackets == null ? null : targetPackets.size());
            return Mono.just(false);
        }
        String appKey = packet.getMessage().getMetadata().getAppKey();
        return Mono.fromCallable(() -> {
                    applyWithdrawnPacketsToRedis(appKey, sessionId, targetPackets);
                    return Boolean.TRUE;
                })
                .doOnError(e -> log.error("撤回 Redis 更新失败 | appKey={}, sessionId={}", appKey, sessionId, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private static boolean isWithdrawTargetPacketsValid(List<Packet> packets) {
        for (Packet withdrawPacket : packets) {
            if (withdrawPacket == null || withdrawPacket.getMessage() == null) {
                return false;
            }
            int contentType = withdrawPacket.getMessage().getContentType();
            if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == contentType
                    || MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == contentType) {
                return false;
            }
        }
        return true;
    }

    @SuppressWarnings("unchecked")
    private void applyWithdrawnPacketsToRedis(String appKey, String sessionId, List<Packet> packets) {
        String sessionCacheKey = CacheConstant.buildSessionCacheKey(appKey, sessionId);
        redisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                for (Packet withdrawPacket : packets) {
                    withdrawPacket.setRetain(NumberConstant.NUMBER_1);
                    operations.opsForValue().set((K) CacheConstant.buildMessageCacheKey(appKey, withdrawPacket.getPacketId()),
                            (V) withdrawPacket, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    String member = MessageContext.idGenerator().formatLongId19Str(withdrawPacket.getPacketId());
                    operations.opsForZSet().remove((K) sessionCacheKey, (V) member);
                }
                return null;
            }
        });
    }
}
