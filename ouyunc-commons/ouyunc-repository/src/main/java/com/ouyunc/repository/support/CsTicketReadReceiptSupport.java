package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.entity.SessionMessageOffsetEntity;
import com.ouyunc.repository.cs.CsImSessionRoute;
import com.ouyunc.repository.cs.CsMessageScopeHelper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
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
    private final StringRedisTemplate stringRedisTemplate;
    private final CsTicketUnreadSupport ticketUnread;
    private final MongoTemplate mongoTemplate;
    private final JdbcClient jdbcClient;

    public CsTicketReadReceiptSupport(SpecialMessageLoader specialMessageLoader,
                                      StringRedisTemplate stringRedisTemplate,
                                      CsTicketUnreadSupport ticketUnread,
                                      MongoTemplate mongoTemplate,
                                      JdbcClient jdbcClient) {
        this.specialMessageLoader = specialMessageLoader;
        this.stringRedisTemplate = stringRedisTemplate;
        this.ticketUnread = ticketUnread;
        this.mongoTemplate = mongoTemplate;
        this.jdbcClient = jdbcClient;
    }

    public Mono<Boolean> reactiveValidCsReadReceiptMessage(Packet packet, CsImSessionRoute route, byte deviceType) {
        if (route == null || StringUtils.isBlank(route.ticketId())) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            return Mono.just(false);
        }
        String readerId = CsMessageScopeHelper.resolveReaderId(message, route);
        if (StringUtils.isBlank(readerId)) {
            return Mono.just(false);
        }
        String ticketId = route.ticketId().trim();
        String appKey = message.getMetadata().getAppKey();
        return specialMessageLoader.reactiveLoadValidatedSpecialPackets(
                        packet, ticketId, MessageIndexScope.CS_TICKET,
                        MessageConstant.MAX_READ_RECEIPT_MESSAGE_COUNT,
                        (specialPackets) -> Mono.just(true),
                        packets -> isReadReceiptTargetPacketsValid(appKey, ticketId, readerId, deviceType, packets))
                .hasElement();
    }

    private boolean isReadReceiptTargetPacketsValid(String appKey, String ticketId, String readerId, byte deviceType,
                                                    List<Packet> packets) {
        long storedOffset = getTicketReadOffset(appKey, ticketId, readerId, deviceType);
        for (Packet readPacket : packets) {
            if (!SpecialMessageTargetValidator.isChatTargetMessage(readPacket)) {
                log.error("已读回执目标类型不允许 | packetId={}", readPacket == null ? null : readPacket.getPacketId());
                return false;
            }
            if (readPacket.getPacketId() < storedOffset) {
                log.error("已读 packetId 小于当前 ticket offset | stored={}", storedOffset);
                return false;
            }
        }
        return true;
    }

    public Mono<Boolean> reactiveCsReadReceiptMessage(Packet packet, CsImSessionRoute route, byte deviceType,
                                                      long expireTime) {
        if (route == null || packet == null || packet.getMessage() == null) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || StringUtils.isBlank(route.ticketId())) {
            return Mono.just(false);
        }
        String readerId = CsMessageScopeHelper.resolveReaderId(message, route);
        if (StringUtils.isBlank(readerId)) {
            return Mono.just(false);
        }
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        Long maxReadPacketId = null;
        if (CollectionUtils.isNotEmpty(readPacketIds)) {
            maxReadPacketId = readPacketIds.stream().max(Comparator.comparingLong(Long::longValue)).orElse(null);
        }
        if (maxReadPacketId == null) {
            log.error("已读的消息 id 不能为空 | packet={}", packet);
            return Mono.just(false);
        }
        final long incomingOffset = maxReadPacketId;
        final String appKey = metadata.getAppKey();
        final String ticketId = route.ticketId().trim();
        return Mono.fromCallable(() -> {
                    ticketUnread.clearOnRead(appKey, ticketId, readerId, deviceType, incomingOffset, expireTime);
                    return Boolean.TRUE;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    public Mono<Boolean> reactiveAdvanceCsSenderReadOffsetOnSend(Packet packet, CsImSessionRoute route, byte deviceType,
                                                                 long expireTime) {
        if (route == null || packet == null || packet.getMessage() == null
                || packet.getMessage().getMetadata() == null || StringUtils.isBlank(route.ticketId())) {
            return Mono.just(false);
        }
        Message message = packet.getMessage();
        String readerId = CsMessageScopeHelper.resolveReaderId(message, route);
        if (StringUtils.isBlank(readerId)) {
            return Mono.just(false);
        }
        return Mono.fromCallable(() -> {
                    ticketUnread.clearOnRead(
                            message.getMetadata().getAppKey(),
                            route.ticketId().trim(),
                            readerId,
                            deviceType,
                            packet.getPacketId(),
                            expireTime);
                    return Boolean.TRUE;
                })
                .subscribeOn(Schedulers.boundedElastic());
    }

    long getTicketReadOffset(String appKey, String ticketId, String readerId, byte deviceType) {
        if (StringUtils.isAnyBlank(appKey, ticketId, readerId)) {
            return 0L;
        }
        String field = com.ouyunc.base.constant.CacheConstant.buildCsTicketReaderDeviceField(readerId, deviceType);
        Object rawObj = stringRedisTemplate.opsForHash().get(
                com.ouyunc.base.constant.CacheConstant.buildCsTicketReadOffsetHashCacheKey(appKey, ticketId.trim()),
                field);
        String raw = rawObj == null ? null : rawObj.toString();
        if (StringUtils.isNotBlank(raw)) {
            try {
                return Long.parseLong(raw.trim());
            } catch (NumberFormatException ex) {
                log.warn("ticket sro 非数字 appKey={} ticketId={} raw={}", appKey, ticketId, raw);
            }
        }
        return loadTicketReadOffsetFromStore(readerId, ticketId, deviceType);
    }

    private long loadTicketReadOffsetFromStore(String readerId, String ticketId, byte deviceType) {
        if (mongoTemplate != null) {
            SessionMessageOffsetEntity row = mongoTemplate.findOne(
                    Query.query(Criteria.where(SessionMessageOffsetEntity.Fields.from).is(readerId)
                            .and(SessionMessageOffsetEntity.Fields.to).is(ticketId)
                            .and(SessionMessageOffsetEntity.Fields.type).is(IdentityType.CUSTOMER_SERVICE.value())
                            .and(SessionMessageOffsetEntity.Fields.deviceType).is(deviceType)),
                    SessionMessageOffsetEntity.class);
            if (row != null && row.getSessionMessageOffset() != null) {
                return row.getSessionMessageOffset();
            }
        }
        if (jdbcClient == null) {
            return 0L;
        }
        try {
            SessionMessageOffsetEntity row = jdbcClient.sql(JdbcSqlDialectHolder.selectSessionMessageOffset())
                    .param(SessionMessageOffsetEntity.Fields.from, readerId)
                    .param(SessionMessageOffsetEntity.Fields.to, ticketId)
                    .param(SessionMessageOffsetEntity.Fields.type, IdentityType.CUSTOMER_SERVICE.value())
                    .param(SessionMessageOffsetEntity.Fields.deviceType, deviceType)
                    .query(SessionMessageOffsetEntity.class)
                    .single();
            Long offset = row.getSessionMessageOffset();
            return offset != null ? offset : 0L;
        } catch (EmptyResultDataAccessException ex) {
            return 0L;
        } catch (Exception ex) {
            log.warn("加载 ticket 已读 offset 失败 reader={} ticketId={}: {}", readerId, ticketId, ex.getMessage());
            return 0L;
        }
    }
}
