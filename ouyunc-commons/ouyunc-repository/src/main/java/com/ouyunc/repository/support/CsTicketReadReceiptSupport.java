package com.ouyunc.repository.support;

import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.MessageIndexScopeEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;

/**
 * 客服咨询单（ticket）维度已读回执与 readOffset。
 */
public final class CsTicketReadReceiptSupport {

    private static final Logger log = LoggerFactory.getLogger(CsTicketReadReceiptSupport.class);

    private final SpecialMessageLoader specialMessageLoader;
    private final CsTicketUnreadSupport ticketUnread;

    public CsTicketReadReceiptSupport(SpecialMessageLoader specialMessageLoader,
                                      CsTicketUnreadSupport ticketUnread) {
        this.specialMessageLoader = specialMessageLoader;
        this.ticketUnread = ticketUnread;
    }

    public Mono<List<Packet>> reactiveLoadValidatedCsReadReceiptPackets(Packet packet, CsImSessionRoute route,
                                                                        byte deviceType) {
        if (route == null || StringUtils.isBlank(route.ticketId())) {
            return Mono.empty();
        }
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            return Mono.empty();
        }
        if (!CsMessageScopeHelper.isVisitorAuthor(message, route)
                && !CsMessageScopeHelper.isStaffAuthor(message, route)) {
            return Mono.empty();
        }
        if (StringUtils.isBlank(CsMessageScopeHelper.authorReadOwner(message, route))) {
            return Mono.empty();
        }
        String ticketId = route.ticketId().trim();
        return specialMessageLoader.reactiveLoadValidatedSpecialPackets(
                packet, ticketId, MessageIndexScopeEnum.CS_TICKET,
                MessageConstant.MAX_READ_RECEIPT_MESSAGE_COUNT,
                (specialPackets) -> Mono.just(true),
                this::isReadReceiptTargetPacketsValid);
    }

    private boolean isReadReceiptTargetPacketsValid(List<Packet> packets) {
        for (Packet readPacket : packets) {
            if (!SpecialMessageTargetValidator.isChatTargetMessage(readPacket)) {
                log.error("已读回执目标类型不允许 | packetId={}", readPacket == null ? null : readPacket.getPacketId());
                return false;
            }
        }
        return true;
    }

    public Mono<Boolean> reactiveCsReadReceiptMessage(Packet packet, CsImSessionRoute route, byte deviceType,
                                                      long expireTime, List<Packet> targetPackets) {
        if (route == null || packet == null || packet.getMessage() == null) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isBlank(route.ticketId())) {
            return Mono.just(false);
        }
        if (!CsMessageScopeHelper.isVisitorAuthor(message, route)
                && !CsMessageScopeHelper.isStaffAuthor(message, route)) {
            return Mono.just(false);
        }
        String ownerId = CsMessageScopeHelper.authorReadOwner(message, route);
        if (StringUtils.isBlank(ownerId)) {
            return Mono.just(false);
        }
        Long maxReadPacketId = null;
        if (CollectionUtils.isNotEmpty(targetPackets)) {
            maxReadPacketId = targetPackets.stream()
                    .map(Packet::getPacketId)
                    .max(Comparator.comparingLong(Long::longValue))
                    .orElse(null);
        }
        if (maxReadPacketId == null) {
            log.error("已读的消息 id 不能为空 | packet={}", packet);
            return Mono.just(false);
        }
        final long incomingOffset = maxReadPacketId;
        final String appKey = metadata.getIngress().getAppKey();
        final String ticketId = route.ticketId().trim();
        return Mono.fromCallable(() -> ticketUnread.getReadOffset(appKey, ownerId, ticketId))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(stored -> {
                    long merged = Math.max(incomingOffset, stored);
                    return Mono.fromCallable(() -> ticketUnread.clearOnRead(
                                    appKey, ownerId, ticketId, merged, expireTime))
                            .subscribeOn(Schedulers.boundedElastic());
                });
    }

    public Mono<Boolean> reactiveAdvanceCsSenderReadOffsetOnSend(Packet packet, CsImSessionRoute route, byte deviceType,
                                                                 long expireTime) {
        return Mono.fromCallable(() -> advanceCsSenderReadOffsetOnSend(packet, route, deviceType, expireTime))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /** 完成器在工作线程确认水位成功，再允许首次投递完成和 ACK。 */
    public boolean advanceCsSenderReadOffsetOnSend(Packet packet, CsImSessionRoute route, byte deviceType,
                                                   long expireTime) {
        if (route == null || packet == null || packet.getMessage() == null
                || packet.getMessage().getMetadata() == null || StringUtils.isBlank(route.ticketId())) {
            return false;
        }
        Message message = packet.getMessage();
        // 系统通知不是坐席/访客作者，不推进水位，也不拦截投递。
        if (!CsMessageScopeHelper.isVisitorAuthor(message, route)
                && !CsMessageScopeHelper.isStaffAuthor(message, route)) {
            return true;
        }
        String ownerId = CsMessageScopeHelper.authorReadOwner(message, route);
        if (StringUtils.isBlank(ownerId)) {
            return false;
        }
        return ticketUnread.clearOnRead(
                        message.getMetadata().getIngress().getAppKey(),
                        ownerId,
                        route.ticketId().trim(),
                        packet.getPacketId(),
                        expireTime);
    }

}
