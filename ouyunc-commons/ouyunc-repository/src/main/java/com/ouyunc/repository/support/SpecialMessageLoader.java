package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * 特殊消息（撤回 / 已读等）引用的目标 Packet 加载与校验。
 */
public final class SpecialMessageLoader {

    private static final Logger log = LoggerFactory.getLogger(SpecialMessageLoader.class);

    private final MessagePacketQuerySupport messagePacketQuery;

    public SpecialMessageLoader(MessagePacketQuerySupport messagePacketQuery) {
        this.messagePacketQuery = messagePacketQuery;
    }

    public Mono<Boolean> reactiveValidSpecialMessage(Packet packet, String scopeId, MessageIndexScope scope, int maxCount,
                                                     Function<List<Packet>, Mono<Boolean>> function,
                                                     Predicate<List<Packet>> extraPredicate) {
        return reactiveLoadValidatedSpecialPackets(packet, scopeId, scope, maxCount, function, extraPredicate)
                .hasElement();
    }

    /** @deprecated channel session scope */
    @Deprecated
    public Mono<Boolean> reactiveValidSpecialMessage(Packet packet, String sessionId, int maxCount,
                                                     Function<List<Packet>, Mono<Boolean>> function,
                                                     Predicate<List<Packet>> extraPredicate) {
        return reactiveValidSpecialMessage(
                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, maxCount, function, extraPredicate);
    }

    public Mono<List<Packet>> reactiveLoadValidatedSpecialPackets(Packet packet, String scopeId, MessageIndexScope scope,
                                                                  int maxCount,
                                                                  Function<List<Packet>, Mono<Boolean>> function,
                                                                  Predicate<List<Packet>> extraPredicate) {
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            log.error("消息或元数据为空");
            return Mono.empty();
        }
        Metadata metadata = message.getMetadata();
        List<Long> packetIds;
        try {
            packetIds = JSON.parseArray(message.getContent(), Long.class);
        } catch (Exception e) {
            log.error("解析消息内容失败", e);
            return Mono.empty();
        }
        if (CollectionUtils.isEmpty(packetIds) || packetIds.size() > maxCount) {
            log.error("消息数量为0或超出限制 {}!", maxCount);
            return Mono.empty();
        }
        String expectedAppKey = metadata.getAppKey();
        return messagePacketQuery.fetchPacketsReactive(expectedAppKey, packetIds)
                .flatMap(packets -> validateLoadedPackets(
                        scopeId, scope, packetIds, packets, function, extraPredicate))
                .onErrorResume(e -> {
                    log.error("消息处理异常 | scope={} scopeId={}", scope, scopeId, e);
                    return Mono.empty();
                });
    }

    /** @deprecated 使用 {@link #reactiveLoadValidatedSpecialPackets(Packet, String, MessageIndexScope, int, Function, Predicate)} */
    @Deprecated
    public Mono<List<Packet>> reactiveLoadValidatedSpecialPackets(Packet packet, String sessionId, int maxCount,
                                                                  Function<List<Packet>, Mono<Boolean>> function,
                                                                  Predicate<List<Packet>> extraPredicate) {
        return reactiveLoadValidatedSpecialPackets(
                packet, sessionId, MessageIndexScope.CHANNEL_SESSION, maxCount, function, extraPredicate);
    }

    private Mono<List<Packet>> validateLoadedPackets(String scopeId, MessageIndexScope scope,
                                                     List<Long> packetIds, List<Packet> packets,
                                                     Function<List<Packet>, Mono<Boolean>> function,
                                                     Predicate<List<Packet>> extraPredicate) {
        if (packets.size() != packetIds.size()) {
            log.error("持久化消息数量不匹配 | scopeId={} | expected={} | actual={}",
                    scopeId, packetIds.size(), packets.size());
            return Mono.empty();
        }
        Set<Long> loadedPacketIds = new HashSet<>();
        for (Packet targetPacket : packets) {
            loadedPacketIds.add(targetPacket.getPacketId());
        }
        if (!loadedPacketIds.containsAll(packetIds)) {
            log.error("持久化消息 id 与请求不匹配 | scopeId={} | packetIds={}", scopeId, packetIds);
            return Mono.empty();
        }
        for (Packet targetPacket : packets) {
            if (!belongsToScope(targetPacket, scopeId, scope)) {
                log.error("消息归属校验失败 | scope={} scopeId={} packetId={}", scope, scopeId, targetPacket.getPacketId());
                return Mono.empty();
            }
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
    }

    static boolean belongsToScope(Packet targetPacket, String scopeId, MessageIndexScope scope) {
        if (scope == MessageIndexScope.CS_TICKET) {
            return belongsToTicketScope(targetPacket, scopeId);
        }
        return belongsToSession(targetPacket, scopeId);
    }

    static boolean belongsToTicketScope(Packet targetPacket, String ticketId) {
        if (targetPacket == null || targetPacket.getMessage() == null || StringUtils.isBlank(ticketId)) {
            return false;
        }
        return StringUtils.equals(ticketId.trim(), targetPacket.getMessage().getCorrelationId());
    }

    static boolean belongsToSession(Packet targetPacket, String sessionId) {
        Message targetMessage = targetPacket.getMessage();
        if (targetMessage == null || StringUtils.isAnyBlank(targetMessage.getFrom(), targetMessage.getTo(), sessionId)) {
            return false;
        }
        if (sessionId.equals(IdentityUtil.sessionId(targetMessage.getFrom(), targetMessage.getTo()))) {
            return true;
        }
        return sessionId.equals(targetMessage.getTo());
    }
}
