package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 特殊消息（撤回 / 已读等）引用的目标 Packet 加载与校验。
 */
public final class SpecialMessageLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecialMessageLoader.class);

    private final SessionIndexSupport sessionIndex;
    private final MessagePacketQuerySupport messagePacketQuery;

    public SpecialMessageLoader(SessionIndexSupport sessionIndex, MessagePacketQuerySupport messagePacketQuery) {
        this.sessionIndex = sessionIndex;
        this.messagePacketQuery = messagePacketQuery;
    }

    public Mono<Boolean> reactiveValidSpecialMessage(Packet packet, String sessionId,
                                                     Function<List<Packet>, Mono<Boolean>> function,
                                                     Predicate<List<Packet>> extraPredicate) {
        return reactiveLoadValidatedSpecialPackets(packet, sessionId, function, extraPredicate)
                .hasElement();
    }

    public Mono<List<Packet>> reactiveLoadValidatedSpecialPackets(Packet packet, String sessionId,
                                                                  Function<List<Packet>, Mono<Boolean>> function,
                                                                  Predicate<List<Packet>> extraPredicate) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        List<Long> packetIds;
        try {
            packetIds = JSON.parseArray(message.getContent(), Long.class);
        } catch (Exception e) {
            log.error("解析消息内容失败", e);
            return Mono.empty();
        }
        if (CollectionUtils.isEmpty(packetIds) || packetIds.size() > MessageConstant.MAX_HANDLE_MESSAGE_COUNT) {
            log.error("消息数量为0或超出限制 {}!", MessageConstant.MAX_HANDLE_MESSAGE_COUNT);
            return Mono.empty();
        }
        List<String> zsetMembers = packetIds.stream()
                .map(MessageContext.idGenerator()::formatLongId19Str)
                .toList();
        String sessionCacheKey = CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId);
        return Mono.fromCallable(() -> sessionIndex.batchZSetScoresPipelined(sessionCacheKey, zsetMembers))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(scores -> {
                    int presentCount = SessionIndexSupport.countPresentZSetScores(scores);
                    if (scores.isEmpty() || presentCount != packetIds.size()) {
                        log.error("会话:{} 不存在该消息id: {}, 或消息id数量与会话中的消息数量不相等", sessionId, packetIds);
                        return Mono.empty();
                    }
                    return messagePacketQuery.fetchPacketsReactive(metadata.getAppKey(), packetIds)
                            .flatMap(packets -> {
                                if (packets.size() != packetIds.size()) {
                                    log.error("持久化消息数量不匹配 | session={} | expected={} | actual={}",
                                            sessionId, packetIds.size(), packets.size());
                                    return Mono.empty();
                                }
                                return function.apply(packets).flatMap(valid -> {
                                    if (!valid) {
                                        return Mono.empty();
                                    }
                                    if (extraPredicate != null && !extraPredicate.test(packets)) {
                                        return Mono.empty();
                                    }
                                    return Mono.just(packets);
                                });
                            });
                })
                .onErrorResume(e -> {
                    log.error("消息处理异常 | session={}", sessionId, e);
                    return Mono.empty();
                });
    }
}
