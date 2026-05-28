package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.LuaScriptEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.domain.entity.SessionMessageOffsetEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * 已读回执校验与 readOffset 更新。
 */
public final class ReadReceiptSupport {

    private static final Logger log = LoggerFactory.getLogger(ReadReceiptSupport.class);

    private final SpecialMessageLoader specialMessageLoader;
    private final RedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final JdbcClient jdbcClient;

    public ReadReceiptSupport(SpecialMessageLoader specialMessageLoader, RedisTemplate redisTemplate,
                              MongoTemplate mongoTemplate, JdbcClient jdbcClient) {
        this.specialMessageLoader = specialMessageLoader;
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.jdbcClient = jdbcClient;
    }

    public Mono<Boolean> reactiveValidReadReceiptMessage(Packet packet, String sessionId, IdentityType identityType,
                                                         boolean isValidSender) {
        return specialMessageLoader.reactiveValidSpecialMessage(packet, sessionId, (specialPackets) -> {
            if (isValidSender) {
                for (Packet specialPacket : specialPackets) {
                    if (specialPacket == null || specialPacket.getMessage().getFrom().equals(packet.getMessage().getFrom())) {
                        log.error("消息id: {} 对应的消息属于发送者！", packet);
                        return Mono.just(false);
                    }
                }
            }
            return Mono.just(true);
        }, (packets) -> {
            Message message = packet.getMessage();
            long storedOffset = resolveMaxReadOffsetAllDevices(
                    message.getMetadata().getAppKey(), identityType, message.getFrom(), message.getTo());
            for (Packet readPacket : packets) {
                if (readPacket.getPacketId() < storedOffset) {
                    log.error("消息id: {} 对应的消息已读id小于当前用户最大已读id: {}！", packet, storedOffset);
                    return false;
                }
            }
            return true;
        });
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
        String offsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                metadata.getAppKey(), identityType.value(), from, packet.getDeviceType(), to);
        final long incomingOffset = maxReadPacketId;
        return Mono.fromCallable(() -> {
                    DefaultRedisScript<Long> readOffsetScript = new DefaultRedisScript<>(
                            LuaScriptEnum.READ_OFFSET_MAX_SCRIPT.getScript(), Long.class);
                    redisTemplate.execute(readOffsetScript, List.of(offsetKey), incomingOffset, expireTime);
                    return Boolean.TRUE;
                })
                .doOnError(e -> log.error("已读回执 Redis 更新失败 | offsetKey={}, incomingOffset={}, expireTime={}",
                        offsetKey, incomingOffset, expireTime, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    @SuppressWarnings("unchecked")
    private Long getSessionMaxReadPackageId(String appKey, IdentityType identityType, String from, Byte deviceType, String to) {
        String sessionMessageOffsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(
                appKey, identityType.value(), from, deviceType, to);
        Long sessionMessageOffset = null;
        Object cachedOffset = redisTemplate.opsForValue().get(sessionMessageOffsetKey);
        if (cachedOffset instanceof Number n) {
            sessionMessageOffset = n.longValue();
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
                redisTemplate.opsForValue().set(sessionMessageOffsetKey, maxSessionMessageOffset,
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

    private long resolveMaxReadOffsetAllDevices(String appKey, IdentityType identityType, String from, String to) {
        Collection<Byte> deviceTypes = MessageContext.deviceTypeList(appKey, from);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return 0L;
        }
        List<Byte> deviceTypeList = Lists.newArrayList(deviceTypes);
        List<String> redisKeys = new ArrayList<>(deviceTypeList.size());
        for (Byte deviceType : deviceTypeList) {
            redisKeys.add(CacheConstant.buildSessionReadMessageOffsetCacheKey(appKey, identityType.value(), from, deviceType, to));
        }
        @SuppressWarnings("unchecked")
        List<Object> cached = redisTemplate.opsForValue().multiGet(redisKeys);

        long max = 0L;
        boolean found = false;
        List<Byte> missingDeviceTypes = new ArrayList<>();
        for (int i = 0; i < deviceTypeList.size(); i++) {
            Object value = cached != null && i < cached.size() ? cached.get(i) : null;
            if (value instanceof Number offset) {
                found = true;
                max = Math.max(max, offset.longValue());
            } else {
                missingDeviceTypes.add(deviceTypeList.get(i));
            }
        }

        for (Byte deviceType : missingDeviceTypes) {
            Long offset = getSessionMaxReadPackageId(appKey, identityType, from, deviceType, to);
            if (offset != null) {
                found = true;
                max = Math.max(max, offset);
            }
        }
        return found ? max : 0L;
    }
}
