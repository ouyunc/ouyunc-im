package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.constant.enums.IdentityType;
import com.ouyunc.domain.entity.SessionMessageOffsetEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 已读回执校验与 readOffset 更新。
 * <p>会话偏移量按设备独立存储；校验仅与本端已存 offset 比较，各端可独立推进。</p>
 * <p>协议说明见 {@code docs/read-receipt-session-offset.md}。</p>
 */
public final class ReadReceiptSupport {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptSupport.class);

    private final SpecialMessageLoader specialMessageLoader;
    private final StringRedisTemplate stringRedisTemplate;
    private final MongoTemplate mongoTemplate;
    private final JdbcClient jdbcClient;
    private final UnreadIndexSupport unreadIndexSupport;

    public ReadReceiptSupport(SpecialMessageLoader specialMessageLoader, StringRedisTemplate stringRedisTemplate,
                              MongoTemplate mongoTemplate, JdbcClient jdbcClient,
                              UnreadIndexSupport unreadIndexSupport) {
        this.specialMessageLoader = specialMessageLoader;
        this.stringRedisTemplate = stringRedisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.jdbcClient = jdbcClient;
        this.unreadIndexSupport = unreadIndexSupport;
    }

    public Mono<Boolean> reactiveValidReadReceiptMessage(Packet packet, String sessionId, IdentityType identityType,
                                                         boolean isValidSender) {
        return specialMessageLoader.reactiveValidSpecialMessage(
                packet, sessionId, MessageConstant.MAX_READ_RECEIPT_MESSAGE_COUNT,
                (specialPackets) -> {
            if (isValidSender) {
                for (Packet specialPacket : specialPackets) {
                    if (specialPacket == null || specialPacket.getMessage() == null
                            || specialPacket.getMessage().getFrom().equals(packet.getMessage().getFrom())) {
                        log.error("消息id: {} 对应的消息属于发送者！", packet);
                        return Mono.just(false);
                    }
                }
            }
            return Mono.just(true);
        }, packets -> isReadReceiptTargetPacketsValid(packet, packets, identityType));
    }

    private boolean isReadReceiptTargetPacketsValid(Packet packet, List<Packet> packets, IdentityType identityType) {
        Message message = packet.getMessage();
        Long deviceStoredOffset = getSessionMaxReadPackageId(
                message.getMetadata().getAppKey(), identityType, message.getFrom(),
                packet.getDeviceType(), message.getTo());
        long storedOffset = deviceStoredOffset != null ? deviceStoredOffset : 0L;
        for (Packet readPacket : packets) {
            if (!SpecialMessageTargetValidator.isChatTargetMessage(readPacket)) {
                log.error("已读回执目标消息内容类型不允许 | packetId={} | contentType={}",
                        readPacket == null ? null : readPacket.getPacketId(),
                        readPacket == null || readPacket.getMessage() == null
                                ? null : readPacket.getMessage().getContentType());
                return false;
            }
            if (readPacket.getPacketId() < storedOffset) {
                log.error("消息id: {} 对应的消息已读id小于当前设备最大已读id: {}！", packet, storedOffset);
                return false;
            }
        }
        return true;
    }

    /**
     * 发送方在本会话发出聊天消息后，静默将本端已读 offset 推进到该消息 packetId（不投递已读回执）。
     */
    public Mono<Boolean> reactiveAdvanceSenderReadOffsetOnSend(Packet packet, IdentityType identityType, long expireTime) {
        Message message = packet.getMessage();
        if (message == null || message.getMetadata() == null) {
            log.warn("发送消息静默更新 offset 失败，消息或元数据为空 | packet={}", packet);
            return Mono.just(false);
        }
        return reactiveUpdateSessionReadOffset(
                message.getMetadata().getAppKey(),
                identityType,
                message.getFrom(),
                packet.getDeviceType(),
                message.getTo(),
                packet.getPacketId(),
                expireTime);
    }

    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveReadReceiptMessage(Packet packet, IdentityType identityType, long expireTime) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        Long maxReadPacketId = null;
        if (CollectionUtils.isNotEmpty(readPacketIds)) {
            maxReadPacketId = readPacketIds.stream().max(Comparator.comparingLong(Long::longValue)).orElse(null);
        }
        if (maxReadPacketId == null) {
            log.error("已读的消息id不能为空 | packet={}", packet);
            return Mono.just(false);
        }
        return reactiveUpdateSessionReadOffset(
                metadata.getAppKey(), identityType, from, packet.getDeviceType(), to, maxReadPacketId, expireTime);
    }

    @SuppressWarnings("unchecked")
    private Mono<Boolean> reactiveUpdateSessionReadOffset(String appKey, IdentityType identityType, String from,
                                                          Byte deviceType, String to, long incomingOffset,
                                                          long expireTime) {
        if (identityType == IdentityType.ONE_2_ONE) {
            return Mono.fromCallable(() -> {
                        unreadIndexSupport.clearOne2OneOnRead(appKey, from, deviceType, to, incomingOffset, expireTime);
                        return Boolean.TRUE;
                    })
                    .doOnError(e -> log.error("单聊已读 offset+未读 更新失败 | reader={}, peer={}, incomingOffset={}",
                            from, to, incomingOffset, e))
                    .subscribeOn(Schedulers.boundedElastic());
        }
        String offsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, identityType.value(), from, deviceType, to);
        return Mono.fromCallable(() -> {
                    DefaultRedisScript<String> readOffsetScript = new DefaultRedisScript<>(
                            LuaScriptEnum.READ_OFFSET_MAX_SCRIPT.getScript(), String.class);
                    stringRedisTemplate.execute(readOffsetScript, List.of(offsetKey),
                            String.valueOf(incomingOffset), String.valueOf(expireTime));
                    return Boolean.TRUE;
                })
                .doOnError(e -> log.error("会话已读 offset Redis 更新失败 | offsetKey={}, incomingOffset={}, expireTime={}",
                        offsetKey, incomingOffset, expireTime, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private Long getSessionMaxReadPackageId(String appKey, IdentityType identityType, String from, Byte deviceType, String to) {
        String sessionMessageOffsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, identityType.value(), from, deviceType, to);
        Long sessionMessageOffset = null;
        String sessionMessageOffsetStr = stringRedisTemplate.opsForValue().get(sessionMessageOffsetKey);
        if (sessionMessageOffsetStr != null && !sessionMessageOffsetStr.isBlank()) {
            sessionMessageOffset =  Long.parseLong(sessionMessageOffsetStr.trim());
        }
        if (sessionMessageOffset != null) {
            return sessionMessageOffset;
        }
        SessionMessageOffsetEntity mongoSessionMessageOffsetEntity = mongoTemplate.findOne(
                new Query(Criteria.where(SessionMessageOffsetEntity.Fields.from).is(from)
                        .and(SessionMessageOffsetEntity.Fields.to).is(to)
                        .and(SessionMessageOffsetEntity.Fields.type).is(identityType.value())
                        .and(SessionMessageOffsetEntity.Fields.deviceType).is(deviceType)).limit(NumberConstant.NUMBER_1),
                SessionMessageOffsetEntity.class);
        if (mongoSessionMessageOffsetEntity != null) {
            return mongoSessionMessageOffsetEntity.getSessionMessageOffset();
        }
        try {
            SessionMessageOffsetEntity sessionMessageOffsetEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectSessionMessageOffset())
                    .param(SessionMessageOffsetEntity.Fields.from, from)
                    .param(SessionMessageOffsetEntity.Fields.to, to)
                    .param(SessionMessageOffsetEntity.Fields.type, identityType.value())
                    .param(SessionMessageOffsetEntity.Fields.deviceType, deviceType)
                    .query(SessionMessageOffsetEntity.class)
                    .single();
            Long maxSessionMessageOffset = sessionMessageOffsetEntity.getSessionMessageOffset();
            if (maxSessionMessageOffset != null) {
                stringRedisTemplate.opsForValue().set(sessionMessageOffsetKey,
                        Long.toString(maxSessionMessageOffset),
                        MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            }
            return maxSessionMessageOffset;
        } catch (EmptyResultDataAccessException e) {
            log.debug("sessionMessageOffsetEntity 不存在, from: {}, to: {}, type: {}", from, to, identityType);
            return null;
        } catch (Exception e) {
            log.error("获取会话偏移量实体异常, from: {}, to:{}, type:{} 原因：{}", from, to, identityType, e.getMessage());
            return null;
        }
    }
}
