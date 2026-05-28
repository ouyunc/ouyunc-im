package com.ouyunc.repository.support;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.JdbcSqlDialectHolder;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.domain.entity.MessageEntity;
import com.ouyunc.domain.entity.MongoMessageEntity;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.jdbc.core.simple.JdbcClient;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 消息 Packet 缓存与 DB 查询。
 */
public final class MessagePacketQuerySupport {

    private static final Logger log = LoggerFactory.getLogger(MessagePacketQuerySupport.class);

    private static Executor dbExecutor() {
        return ThreadPoolManager.repositoryExecutor();
    }

    private final RedisTemplate redisTemplate;
    private final MongoTemplate mongoTemplate;
    private final JdbcClient jdbcClient;

    public MessagePacketQuerySupport(RedisTemplate redisTemplate, MongoTemplate mongoTemplate, JdbcClient jdbcClient) {
        this.redisTemplate = redisTemplate;
        this.mongoTemplate = mongoTemplate;
        this.jdbcClient = jdbcClient;
    }

    @SuppressWarnings("unchecked")
    public List<Packet> getPackets(String appKey, List<Long> packetIds) {
        if (CollectionUtils.isEmpty(packetIds)) {
            log.warn("packetIds 为空, appKey={}", appKey);
            return Collections.emptyList();
        }

        List<String> redisKeys = packetIds.stream()
                .map(id -> CacheConstant.buildMessageCacheKey(appKey, id))
                .collect(Collectors.toList());
        List<Packet> cachedPackets = (List<Packet>) redisTemplate.opsForValue().multiGet(redisKeys);
        if (cachedPackets == null) {
            log.warn("cachedPackets 为空, appKey={}", appKey);
            return Collections.emptyList();
        }
        Map<Long, Packet> cachedPacketMap = cachedPackets.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Packet::getPacketId, Function.identity(), (a, b) -> a));
        Set<Long> cachedIds = cachedPacketMap.keySet();

        if (cachedIds.size() == packetIds.size()) {
            return new ArrayList<>(cachedPacketMap.values());
        }

        List<Long> missingIds = packetIds.stream()
                .filter(id -> !cachedIds.contains(id))
                .collect(Collectors.toList());

        List<Packet> dbPackets = queryPacketsFromDatabases(missingIds);

        List<Packet> result = new ArrayList<>(cachedPacketMap.values());
        result.addAll(dbPackets);
        asyncUpdatePacketCache(appKey, dbPackets);
        return result;
    }

    public Mono<List<Packet>> fetchPacketsReactive(String appKey, List<Long> packetIds) {
        return Mono.fromCallable(() -> getPackets(appKey, packetIds))
                .subscribeOn(Schedulers.boundedElastic());
    }

    private List<Packet> queryPacketsFromDatabases(List<Long> missingIds) {
        if (CollectionUtils.isEmpty(missingIds)) {
            return Collections.emptyList();
        }

        List<MongoMessageEntity> mongoEntities = mongoTemplate.find(
                Query.query(Criteria.where(MongoMessageEntity.Fields.id).in(missingIds)),
                MongoMessageEntity.class
        );
        List<Packet> dbPackets = convertToPackets(mongoEntities);

        Set<Long> foundIds = mongoEntities.stream()
                .map(MessageEntity::getId)
                .collect(Collectors.toSet());
        List<Long> remainingIds = missingIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());

        if (!CollectionUtils.isEmpty(remainingIds)) {
            try {
                List<MessageEntity> mysqlEntities = jdbcClient.sql(JdbcSqlDialectHolder.selectMessage())
                        .param(MessageEntity.Fields.ids, remainingIds)
                        .query(MessageEntity.class)
                        .list();
                dbPackets.addAll(convertToPackets(mysqlEntities));
            } catch (EmptyResultDataAccessException e) {
                log.error("message不存在, 原因: {}", e.getMessage());
                return dbPackets;
            } catch (Exception e) {
                log.error("获取消息实体异常, remainingIds: {}, 原因：{}", remainingIds, e.getMessage());
                return dbPackets;
            }
        }

        return dbPackets;
    }

    private Packet convertToPacket(MessageEntity entity) {
        return new Packet(
                entity.getProtocol(),
                entity.getProtocolVersion(),
                entity.getId(),
                entity.getDeviceType(),
                entity.getNetworkType(),
                entity.getEncryptType(),
                entity.getSerializeAlgorithm(),
                entity.getMessageType(),
                entity.getRetain(),
                new Message(
                        entity.getMessageId(),
                        entity.getFrom(),
                        entity.getFromType(),
                        entity.getTo(),
                        entity.getToType(),
                        entity.getContentType(),
                        entity.getContent(),
                        JSON.parseArray(entity.getAt(), String.class),
                        JSON.parseArray(entity.getRef(), String.class),
                        entity.getExtra(),
                        entity.getQos(),
                        entity.getClientSendTime(),
                        new Metadata(
                                entity.getAppKey(),
                                entity.getClientIp(),
                                entity.getServerArrivalTime()
                        )
                )
        );
    }

    private List<Packet> convertToPackets(List<? extends MessageEntity> entities) {
        return entities.stream()
                .filter(Objects::nonNull)
                .map(this::convertToPacket)
                .collect(Collectors.toList());
    }

    @SuppressWarnings("unchecked")
    private void asyncUpdatePacketCache(String appKey, List<Packet> dbPackets) {
        if (CollectionUtils.isEmpty(dbPackets)) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    dbPackets.forEach(packet -> {
                        operations.opsForValue().set((K) CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), (V) packet, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    });
                    return null;
                }
            });
        }, dbExecutor()).exceptionally(ex -> {
            log.error("异步更新缓存失败, appKey={}, packetSize={}", appKey, dbPackets.size(), ex);
            return null;
        });
    }
}
