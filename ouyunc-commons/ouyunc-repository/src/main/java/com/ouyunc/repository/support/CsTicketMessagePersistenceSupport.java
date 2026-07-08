package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.SaveMessageOutcome;
import com.ouyunc.repository.cs.CsImSessionRoute;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 客服咨询单（ticket）维度消息持久化：写入 ticket msgs ZSet，并维护 ticket 未读。
 */
public final class CsTicketMessagePersistenceSupport {

    private final SessionMessagePersistenceSupport sessionPersistence;
    private final CsTicketUnreadSupport ticketUnread;

    public CsTicketMessagePersistenceSupport(SessionMessagePersistenceSupport sessionPersistence,
                                             CsTicketUnreadSupport ticketUnread) {
        this.sessionPersistence = sessionPersistence;
        this.ticketUnread = ticketUnread;
    }

    public Mono<Boolean> reactiveSaveCsTicketMessage(Packet packet, CsImSessionRoute route, long expireTime) {
        if (route == null || packet == null || packet.getMessage() == null) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || metadata.getAppKey() == null || route.ticketId() == null) {
            return Mono.just(false);
        }
        String ticketScopeId = route.ticketId().trim();
        String messageKey = CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId());
        String ticketSessionKey = CacheConstant.buildCsTicketMessageSessionCacheKey(metadata.getAppKey(), ticketScopeId);
        return Mono.fromCallable(() -> {
                    SaveMessageOutcome outcome = sessionPersistence.saveMessageWithSessionOutcome(
                            packet, expireTime, messageKey, ticketSessionKey, (ops) -> {
                            }, (ops, msg, app, f, t) -> {
                            });
                    if (outcome == SaveMessageOutcome.SUCCESS) {
                        ticketUnread.incrOnMessage(packet, route);
                    }
                    return SessionMessagePersistenceSupport.isSaveAccepted(outcome);
                })
                .subscribeOn(Schedulers.boundedElastic())
                .onErrorResume(e -> Mono.just(false));
    }
}
