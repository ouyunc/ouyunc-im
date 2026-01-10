package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.constants.GroupUserPost;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.domain.entity.*;
import com.ouyunc.mq.kafka.KafkaFactory;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import java.nio.ByteBuffer;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.function.*;
import java.util.stream.Collectors;

/**
 * @author fzx
 * @description 默认持久化仓库实现,注意如果子类不进行覆盖，则使用默认的操作器来处理数据
 */
public enum DefaultRepository implements Repository{
    INSTANCE;

    private static Executor dbExecutor() {
        return ThreadPoolManager.repositoryExecutor();
    }

    private static final Logger log = LoggerFactory.getLogger(DefaultRepository.class);
    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();

    /**
     * jdbcClient
     */
    private static final JdbcClient jdbcClient = JdbcFactory.JDBC_CLIENT.instance();


    /**
     * mongoTemplate
     */
    private static final MongoTemplate mongoTemplate = MongodbFactory.MONGODB_TEMPLATE.instance();

    /**
     * reactiveMongoTemplate
     */
    private static final ReactiveMongoTemplate reactiveMongoTemplate = MongodbFactory.REACTIVE_MONGODB_TEMPLATE.instance();

    /**
     * redisTemplate
     */
    private static final RedisTemplate redisTemplate = CacheFactory.REDIS.instance();


    /**
     * ReactiveStringRedisTemplate
     */
    private static final ReactiveStringRedisTemplate reactiveStringRedisTemplate = CacheFactory.REACTIVE_STRING_REDIS.instance();

    /**
     * reactiveRedisTemplate
     */
    private static final ReactiveRedisTemplate reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

    /**
     * stringRedisTemplate
     */
    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * 保存消息到磁盘中，这里使用mq来提高吞吐量；注意这里mq 消费者执行的逻辑是保存消息（持久化），这里给出一个参考实例：
     * 如果不想发送mq 可以在该方法中直接使用如下代码,返回值类型是Future，或者使用其他持久化存储逻辑
     *             log.debug("保存消息: {} 到数据库和mongodb 中", packet);
     *             // 在事务中执行
     *             Message message = packet.getMessage();
     *             Metadata metadata = message.getMetadata();
     *             Boolean executeResult = JdbcFactory.JDBC_TEMPLATE.withTransaction().execute(status -> {
     *                 try {
     *                     String atJson = message.getAt() == null ? null : JSON.toJSONString(message.getAt());
     *                     jdbcTemplate.update(JdbcSqlConstant.MYSQL.INSERT_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getMessageType(), packet.getRetain(), metadata.getClientIp(), message.getFrom(), message.getTo(), message.getContentType(), message.getContent(), message.getExtra(), atJson, message.getQos(), message.getCreateTime(), metadata.getServerTime(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_0);
     *                     // 保存到mongodb 默认时效三个月，可根据配置文件配置
     *                     mongoTemplate.insert(new MessageEntity(packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getMessageType(), packet.getRetain(), metadata.getClientIp(), message.getFrom(), message.getTo(), message.getContentType(), message.getContent(), message.getQos(), message.getAt(),message.getExtra(), message.getCreateTime(), metadata.getServerTime(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_0, LocalDateTime.now().plusMonths(NumberConstant.NUMBER_3)));
     *                 } catch (Exception e) {
     *                     log.error("保存消息到数据库和mongodb 中异常: {}", e.getMessage());
     *                     status.setRollbackOnly();
     *                     return false;
     *                 }
     *                 return true;
     *             });
     *             if (Boolean.FALSE.equals(executeResult)) {
     *                 log.error("保存消息到数据库和mongodb 中事务异常");
     *             }
     *
     *
     * @param packet
     * @return
     */
    @Override
    public CompletableFuture<?> save(Packet packet) {
      return savePacket2Mq(MqConstant.KAFKA_SAVE_MESSAGE_TOPIC, null, packet);
    }


    /**
     * 将消息保存到mq中
     * @param topic
     * @param packet
     * @return
     */
    public CompletableFuture<?> savePacket2Mq(String topic, String key, Packet packet) {
        // 保存消息到磁盘中，这里使用mq来提高吞吐量；如果kafka
        // headers 中可以自定义一些信息做扩展；
        Map<String, Object> headers = new HashMap<>();
        headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
        // 如果业务逻辑需要mq保证顺序性消费，请使用相同key， 并且在消费者保证单线程消费
        if (StringUtils.isNotBlank(key)) {
            headers.put(KafkaHeaders.KEY, key);
        }
        headers.put(KafkaHeaders.TOPIC, topic);
        return kafkaTemplate.send(MessageBuilder.withPayload(JSON.toJSONString(packet)).copyHeadersIfAbsent(headers).build());
    }

    /**
     * 检查消息是否重复,是否持久化到缓存，这里是离线队列中
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean checkDup(Packet packet, DeviceType deviceType) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        Double score = stringRedisTemplate.opsForZSet().score(CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), message.getTo(), deviceType.getDeviceTypeValue()), SnowflakeUtil.formatLong(packet.getPacketId()));
        // 如果分数不为 null，则表示值存在
        return !Objects.isNull(score);
    }


    /**
     * 批量获取消息（同步版本，保留兼容性）
     * @param appKey
     * @param packetIds
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Packet> getPackets(String appKey, List<Long> packetIds) {
        if (CollectionUtils.isEmpty(packetIds)) {
            log.warn("packetIds 为空, appKey={}", appKey);
            return Collections.emptyList();
        }

        // 1. 从 Redis 批量获取缓存
        Set<String> redisKeys = packetIds.stream()
                .map(id -> CacheConstant.buildMessageCacheKey(appKey, id))
                .collect(Collectors.toSet());
        List<Packet> cachedPackets = (List<Packet>) redisTemplate.opsForValue().multiGet(redisKeys);
        if (cachedPackets == null) {
            log.warn("cachedPackets 为空, appKey={}", appKey);
            return Collections.emptyList();
        }
        // 过滤有效缓存并收集已存在的 ID
        Map<Long, Packet> cachedPacketMap = cachedPackets.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Packet::getPacketId, Function.identity()));
        List<Long> cachedIds = new ArrayList<>(cachedPacketMap.keySet());

        // 全部命中缓存则直接返回
        if (cachedIds.size() == packetIds.size()) {
            return new ArrayList<>(cachedPacketMap.values());
        }

        // 2. 收集未命中缓存的 ID
        List<Long> missingIds = packetIds.stream()
                .filter(id -> !cachedIds.contains(id))
                .collect(Collectors.toList());

        // 3. 从 MongoDB 和 MySQL 查询缺失数据
        List<Packet> dbPackets = queryPacketsFromDatabases(missingIds);

        // 4. 合并结果并异步更新缓存
        List<Packet> result = mergeResults(cachedPacketMap, dbPackets);
        asyncUpdatePacketCache(appKey, dbPackets);

        return result;
    }

    /**
     * 响应式批量获取消息（优化版本：非阻塞、高并发）
     * @param appKey
     * @param packetIds
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<List<Packet>> getPacketsReactive(String appKey, List<Long> packetIds) {
        if (CollectionUtils.isEmpty(packetIds)) {
            return Mono.just(Collections.emptyList());
        }

        // 1. 从 Redis 批量获取缓存（响应式）
        Set<String> redisKeys = packetIds.stream()
                .map(id -> CacheConstant.buildMessageCacheKey(appKey, id))
                .collect(Collectors.toSet());
        
        return Flux.fromIterable(redisKeys)
                .flatMap(key -> reactiveRedisTemplate.opsForValue().get(key)
                        .cast(Packet.class)
                        .onErrorResume(e -> Mono.empty()))
                .collectMap(packet -> ((Packet) packet).getPacketId(), Function.identity(), HashMap::new)
                .cast(Map.class)
                .flatMap(cachedPacketMapObj -> {
                    @SuppressWarnings("unchecked")
                    Map<Long, Packet> cachedPacketMap = (Map<Long, Packet>) cachedPacketMapObj;
                    List<Long> cachedIds = new ArrayList<>(cachedPacketMap.keySet());
                    
                    // 全部命中缓存则直接返回
                    if (cachedIds.size() == packetIds.size()) {
                        return Mono.just(new ArrayList<>(cachedPacketMap.values()));
                    }
                    
                    // 2. 收集未命中缓存的 ID
                    List<Long> missingIds = packetIds.stream()
                            .filter(id -> !cachedIds.contains(id))
                            .collect(Collectors.toList());
                    
                    // 3. 从 MongoDB 和 MySQL 查询缺失数据（响应式）
                    return queryPacketsFromDatabasesReactive(missingIds)
                            .collectList()
                            .map(dbPackets -> {
                                // 4. 合并结果并异步更新缓存
                                List<Packet> result = mergeResults(cachedPacketMap, dbPackets);
                                asyncUpdateCachePacketReactive(appKey, dbPackets);
                                return result;
                            });
                })
                .onErrorResume(e -> {
                    log.error("响应式批量获取消息异常, appKey: {}, packetIds: {}", appKey, packetIds, e);
                    return Mono.just(Collections.emptyList());
                });
    }

//----------------------------- 辅助方法 -----------------------------



    /**
     * 从 MongoDB 和 MySQL 查询数据 (优先级: MongoDB -> MySQL) - 同步版本
     */
    private List<Packet> queryPacketsFromDatabases(List<Long> missingIds) {
        if (CollectionUtils.isEmpty(missingIds)) {
            return Collections.emptyList();
        }

        // 优先查询 MongoDB
        List<MongoMessageEntity> mongoEntities = mongoTemplate.find(
                Query.query(Criteria.where(MongoMessageEntity.Fields.id).in(missingIds)),
                MongoMessageEntity.class
        );
        List<Packet> dbPackets = convertToPackets(mongoEntities);

        // 检查是否还有缺失
        Set<Long> foundIds = mongoEntities.stream()
                .map(MessageEntity::getId)
                .collect(Collectors.toSet());
        List<Long> remainingIds = missingIds.stream()
                .filter(id -> !foundIds.contains(id))
                .collect(Collectors.toList());

        // 剩余 ID 查询 MySQL
        if (!CollectionUtils.isEmpty(remainingIds)) {
            try {
                List<MessageEntity> mysqlEntities = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_MESSAGE.sql())
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

    /**
     * 响应式从 MongoDB 和 MySQL 查询数据 (优先级: MongoDB -> MySQL) - 优化版本
     */
    private Flux<Packet> queryPacketsFromDatabasesReactive(List<Long> missingIds) {
        if (CollectionUtils.isEmpty(missingIds)) {
            return Flux.empty();
        }

        // 优先查询 MongoDB（响应式）
        return reactiveMongoTemplate.find(
                Query.query(Criteria.where(MongoMessageEntity.Fields.id).in(missingIds)),
                MongoMessageEntity.class
        )
        .map(this::convertToPacket)
        .collectList()
        .flatMapMany(mongoPackets -> {
            // 检查是否还有缺失
            Set<Long> foundIds = mongoPackets.stream()
                    .map(Packet::getPacketId)
                    .collect(Collectors.toSet());
            List<Long> remainingIds = missingIds.stream()
                    .filter(id -> !foundIds.contains(id))
                    .collect(Collectors.toList());

            // 剩余 ID 查询 MySQL
            if (CollectionUtils.isEmpty(remainingIds)) {
                return Flux.fromIterable(mongoPackets);
            }

            return Flux.fromIterable(mongoPackets)
                    .concatWith(
                            Mono.fromCallable(() -> {
                                try {
                                    List<MessageEntity> mysqlEntities = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_MESSAGE.sql())
                                            .param(MessageEntity.Fields.ids, remainingIds)
                                            .query(MessageEntity.class)
                                            .list();
                                    return convertToPackets(mysqlEntities);
                                } catch (Exception e) {
                                    log.error("从MySQL查询消息异常, remainingIds: {}", remainingIds, e);
                                    return Collections.<Packet>emptyList();
                                }
                            })
                            .subscribeOn(Schedulers.fromExecutor(dbExecutor()))
                            .flatMapMany(Flux::fromIterable)
                    );
        })
        .onErrorResume(e -> {
            log.error("响应式查询消息异常, missingIds: {}", missingIds, e);
            return Flux.empty();
        });
    }

    /**
     * 转换单个 MessageEntity 到 Packet（用于响应式流）
     */
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
                        entity.getFrom(),
                        entity.getTo(),
                        entity.getContentType(),
                        entity.getContent(),
                        JSON.parseArray(entity.getAt(), String.class),
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

    /**
     * 转换 MessageEntity 到 Packet
     */
    private List<Packet> convertToPackets(List<? extends MessageEntity> entities) {
        return entities.stream()
                .filter(Objects::nonNull)
                .map(entity -> new Packet(
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
                                entity.getFrom(),
                                entity.getTo(),
                                entity.getContentType(),
                                entity.getContent(),
                                JSON.parseArray(entity.getAt(), String.class),
                                entity.getExtra(),
                                entity.getQos(),
                                entity.getClientSendTime(),
                                new Metadata(
                                        entity.getAppKey(),
                                        entity.getClientIp(),
                                        entity.getServerArrivalTime()
                                )
                        )
                ))
                .collect(Collectors.toList());
    }

    /**
     * 合并缓存和数据库结果
     */
    private List<Packet> mergeResults(Map<Long, Packet> cachedPackets, List<Packet> dbPackets) {
        List<Packet> result = new ArrayList<>(cachedPackets.values());
        result.addAll(dbPackets);
        return result;
    }

    /**
     * 异步更新缓存 (非阻塞主流程) - 同步版本
     */
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
            log.error("异步更新缓存失败", ex);
            return null;
        });
    }

    /**
     * 响应式异步更新缓存 (非阻塞、高并发) - 优化版本
     */
    @SuppressWarnings("unchecked")
    private void asyncUpdateCachePacketReactive(String appKey, List<Packet> dbPackets) {
        if (CollectionUtils.isEmpty(dbPackets)) {
            return;
        }
        // 使用响应式方式批量更新缓存，提高性能
        Flux.fromIterable(dbPackets)
                .flatMap(packet -> {
                    String cacheKey = CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId());
                    return reactiveRedisTemplate.opsForValue()
                            .set(cacheKey, packet, Duration.ofMillis(MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP))
                            .onErrorResume(e -> {
                                log.debug("更新缓存失败, cacheKey: {}", cacheKey, e);
                                return Mono.just(false);
                            });
                })
                .subscribe(
                        result -> {},
                        error -> log.error("响应式批量更新缓存异常", error),
                        () -> log.debug("响应式批量更新缓存完成, count: {}", dbPackets.size())
                );
    }

    private Mono<Set<GroupUserEntity>> fetchGroupUsersFromLowerTiers(String appKey, String groupId, Set<String> remainingMemberIds, Set<GroupUserEntity> acc) {
        if (CollectionUtils.isEmpty(remainingMemberIds)) {
            return Mono.just(acc);
        }

        Long groupIdLong = parseLongSafely(groupId);
        List<Long> userIdList = remainingMemberIds.stream()
                .map(this::parseLongSafely)
                .filter(Objects::nonNull)
                .toList();

        if (groupIdLong == null || userIdList.isEmpty()) {
            return Mono.just(acc);
        }

        return reactiveMongoTemplate.find(
                        Query.query(Criteria.where(MongoGroupUserEntity.Fields.groupId).is(groupIdLong)
                                .and(MongoGroupUserEntity.Fields.userId).in(userIdList)),
                        MongoGroupUserEntity.class)
                .map(this::convertMongoGroupUserToGroupUser)
                .collectList()
                .flatMap(mongoEntities -> {
                    Set<String> remaining = new HashSet<>(remainingMemberIds);
                    for (GroupUserEntity entity : mongoEntities) {
                        if (entity == null || entity.getUserId() == null) {
                            continue;
                        }
                        String userKey = String.valueOf(entity.getUserId());
                        remaining.remove(userKey);
                        updateGroupUserCache(CacheConstant.buildGroupUserConfigCacheKey(appKey, userKey, groupId), entity);
                        acc.add(entity);
                    }

                    if (CollectionUtils.isEmpty(remaining)) {
                        return Mono.just(acc);
                    }

                    return Mono.fromCallable(() -> getGroupUsersFromDatabases(groupId, remaining))
                            .subscribeOn(Schedulers.fromExecutor(dbExecutor()))
                            .map(dbEntities -> {
                                for (GroupUserEntity entity : dbEntities) {
                                    if (entity == null || entity.getUserId() == null) {
                                        continue;
                                    }
                                    String userKey = String.valueOf(entity.getUserId());
                                    updateGroupUserCache(CacheConstant.buildGroupUserConfigCacheKey(appKey, userKey, groupId), entity);
                                    acc.add(entity);
                                }
                                return acc;
                            })
                            .onErrorResume(e -> {
                                log.error("批量查询群成员数据库异常, appKey: {}, groupId: {}, memberIds: {}", appKey, groupId, remaining, e);
                                return Mono.just(acc);
                            })
                            .defaultIfEmpty(acc);
                })
                .defaultIfEmpty(acc);
    }

    /**
     * 同步方式从MongoDB和MySQL批量查询群成员（通过修改resultMap返回结果）
     * @param appKey 应用key
     * @param groupId 群组ID
     * @param remainingMemberIds 待查询的成员ID集合
     * @param memberIdCacheKeyMap 成员ID到缓存key的映射
     * @param resultMap 结果Map（会被修改，查询到的群成员会添加到这个Map中）
     */
    private void fetchGroupUsersFromLowerTiersSync(String appKey, String groupId, Set<String> remainingMemberIds,
                                                   Map<String, String> memberIdCacheKeyMap,
                                                   Map<String, GroupUserEntity> resultMap) {
        if (CollectionUtils.isEmpty(remainingMemberIds)) {
            return;
        }

        Long groupIdLong = parseLongSafely(groupId);
        List<Long> userIdList = remainingMemberIds.stream()
                .map(this::parseLongSafely)
                .filter(Objects::nonNull)
                .toList();

        if (groupIdLong == null || userIdList.isEmpty()) {
            return;
        }

        // MongoDB
        List<MongoGroupUserEntity> mongoEntities = mongoTemplate.find(
                Query.query(Criteria.where(MongoGroupUserEntity.Fields.groupId).is(groupIdLong)
                        .and(MongoGroupUserEntity.Fields.userId).in(userIdList)),
                MongoGroupUserEntity.class);

        Set<String> remaining = new HashSet<>(remainingMemberIds);
        if (CollectionUtils.isNotEmpty(mongoEntities)) {
            for (MongoGroupUserEntity mongoEntity : mongoEntities) {
                GroupUserEntity entity = convertMongoGroupUserToGroupUser(mongoEntity);
                if (entity == null || entity.getUserId() == null) {
                    continue;
                }
                String memberId = String.valueOf(entity.getUserId());
                String cacheKey = memberIdCacheKeyMap.get(memberId);
                updateGroupUserCache(cacheKey, entity);
                resultMap.put(memberId, entity);
                remaining.remove(memberId);
            }
        }

        if (CollectionUtils.isEmpty(remaining)) {
            return;
        }

        // MySQL
        List<GroupUserEntity> dbEntities = getGroupUsersFromDatabases(groupId, remaining);
        if (CollectionUtils.isNotEmpty(dbEntities)) {
            for (GroupUserEntity entity : dbEntities) {
                if (entity == null || entity.getUserId() == null) {
                    continue;
                }
                String memberId = String.valueOf(entity.getUserId());
                String cacheKey = memberIdCacheKeyMap.get(memberId);
                updateGroupUserCache(cacheKey, entity);
                resultMap.put(memberId, entity);
                remaining.remove(memberId);
            }
        }

        // 剩余的成员（若有）保持未命中状态
    }

    private List<GroupUserEntity> getGroupUsersFromDatabases(String groupId, Set<String> memberIds) {
        if (CollectionUtils.isEmpty(memberIds)) {
            return Collections.emptyList();
        }

        Long groupIdLong = parseLongSafely(groupId);
        if (groupIdLong == null) {
            return Collections.emptyList();
        }

        List<Long> userIdList = memberIds.stream()
                .map(this::parseLongSafely)
                .filter(Objects::nonNull)
                .toList();

        if (userIdList.isEmpty()) {
            return Collections.emptyList();
        }

        try {
            return jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_GROUP_USER_BATCH.sql())
                    .param(GroupUserEntity.Fields.groupId, groupIdLong)
                    .param(GroupUserEntity.Fields.userIds, userIdList)
                    .query(GroupUserEntity.class)
                    .list();
        } catch (EmptyResultDataAccessException e) {
            log.warn("批量查询群成员为空, groupId: {}, memberIds: {}", groupId, memberIds);
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("批量查询群成员异常, groupId: {}, memberIds: {}", groupId, memberIds, e);
            return Collections.emptyList();
        }
    }

    private Long parseLongSafely(String value) {
        if (StringUtils.isBlank(value)) {
            return null;
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("无法解析为Long类型，值: {}", value);
            return null;
        }
    }


    /**
     * 撤回消息校验,其实撤回消息的校验，应该加上，只能撤回某种类型的消息，比如私聊和群聊消息，重复撤回某个消息目前也没有做校验，不过在第一次撤回的时候会将被撤回的消息从会话中剔除，后面的重复撤回自然查不到该数据也就不存在多次撤回了，下游数据按道理也不会接收到
     * @param packet
     * @param sessionId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveValidWithdrawMessage(Packet packet, String sessionId, boolean isValidSender) {
        return reactiveValidSpecialMessage(packet, sessionId, (specialPackets)->{
            // 判断消息是否属于该会话，且都属于发送者； 这里考虑个问题，如果是群主或者管理员，需要让其撤销消息？应该是可以撤销的
            if (isValidSender) {
                for (Packet specialPacket : specialPackets) {
                    if (specialPacket == null || specialPacket.getMessage() == null || !specialPacket.getMessage().getFrom().equals(packet.getMessage().getFrom())) {
                        log.error("消息: {} 对应的消息不属于发送者！", packet);
                        return Mono.just(false);
                    }
                }
            }
            return Mono.just(true);
        }, (packets)->{
            // 如果可以调用该方法的，一定是群聊或者私聊类型
            for (Packet withdrawPacket : packets) {
                Message message = withdrawPacket.getMessage();
                if (MessageContentTypeEnum.WITHDRAW_CONTENT.getType() == message.getContentType() || MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType() == message.getContentType()) {
                    log.error("消息: {} 对应的消息内容类型：{} 错误！，不允许撤回撤回消息或已读消息", packet, message.getContentType());
                    return false;
                }
            }
            // 校验通过后，直接设置packet 的retain 保留字段作为撤回消息的标志，1 撤回，0 不撤回,并设置到redis 缓存中
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    for (Packet withdrawPacket : packets) {
                        withdrawPacket.setRetain(NumberConstant.NUMBER_1);
                        operations.opsForValue().set((K) CacheConstant.buildMessageCacheKey(withdrawPacket.getMessage().getMetadata().getAppKey(), withdrawPacket.getPacketId()), (V) withdrawPacket, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    }
                    return null;
                }
            });
            return true;
        });
    }



    /**
     * 响应式处理无锁逻辑
     *
     * @param ctx
     * @param packet
     * @param validator
     * @param mqSender
     * @param processor
     */
    public Mono<Boolean> reactiveHandleOperation(ChannelHandlerContext ctx, Packet packet,
                                                 Mono<Boolean> validator,
                                                 Supplier<CompletableFuture<?>> mqSender,
                                                 Mono<Boolean> processor,
                                                 BiConsumer<ChannelHandlerContext, Packet> processorAfter,
                                                 Consumer<ExceptionEvent> exceptionConsumer,
                                                 ExceptionCodeEnum exceptionCode) {
        return validator.flatMap(valid -> {
            if (!valid) {
                exceptionConsumer.accept(new ExceptionEvent(exceptionCode, packet));
                return Mono.just(false);
            }
            // 优化后的代码片段，确保MQ发送成功后才执行processor
            return Mono.fromFuture(mqSender.get())
                    // 当MQ发送成功时，返回一个表示成功的Mono
                    .thenReturn(true)
                    .onErrorResume(ex -> {
                        // MQ发送失败时的处理
                        exceptionConsumer.accept(new ExceptionEvent(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR,  ex.getMessage(), packet));
                        return Mono.just(false); // 明确返回失败标识
                    })
                    // 只有当MQ发送成功（上一步返回true）时才执行processor
                    .flatMap(mqSentSuccessfully -> {
                        if (mqSentSuccessfully) {
                            return processor; // MQ发送成功，执行业务处理
                        } else {
                            return Mono.just(false); // MQ发送失败，直接返回失败
                        }
                    })
                    .doOnNext(processed -> {
                        if (processed) {
                            processorAfter.accept(ctx, packet);
                        } else {
                            exceptionConsumer.accept(new ExceptionEvent(ExceptionCodeEnum.UNKNOWN_ERROR,  "撤销或已读异常", packet));
                        }
                    })
                    .onErrorResume(ex -> {
                        exceptionConsumer.accept(new ExceptionEvent(ExceptionCodeEnum.UNKNOWN_ERROR,  ex.getMessage(), packet));
                        return Mono.just(false);
                    });
        });

    }


    /**
     * 响应式撤回消息校验, 这里没有校验重复已读某个消息，如果有需求，后续可以加上，因为会增加额外的查询耗时
     * @param packet
     * @param sessionId
     * @return
     */
    public Mono<Boolean> reactiveValidReadReceiptMessage(Packet packet, String sessionId, IdentityType identityType,  boolean isValidSender) {
        return reactiveValidSpecialMessage(packet, sessionId, (specialPackets)->{
            // 判断消息是否属于该会话，且都属于发送者
            if (isValidSender) {
                for (Packet specialPacket : specialPackets) {
                    if (specialPacket == null || specialPacket.getMessage().getFrom().equals(packet.getMessage().getFrom())) {
                        log.error("消息id: {} 对应的消息属于发送者！", packet);
                        return Mono.just(false);
                    }
                }
            }
            return Mono.just(true);
        }, (packets)->{
            // 获取当前会话中用户的最大已读id，如果已读id小于当前用户最大已读id，则返回false,消息已读默认不允许回退，只能往后已读
            Message message = packet.getMessage();
            Long sessionMessageOffset = getSessionMaxReadPackageId(message.getMetadata().getAppKey(), identityType, message.getFrom(), packet.getDeviceType(), message.getTo());
            if (sessionMessageOffset == null) {
                log.error("消息id: {} 对应的消息已读id为空！", packet);
                return false;
            }
            for (Packet readPacket : packets) {
                // 获取已读id
                if (readPacket.getPacketId() < sessionMessageOffset) {
                    log.error("消息id: {} 对应的消息已读id小于当前用户最大已读id: {}！", packet, sessionMessageOffset);
                    return false;
                }
            }
            return true;
        });
    }

    /**
     * 获取会话最大已读id
     * @param from
     * @param to
     * @return
     */
    @SuppressWarnings("unchecked")
    private Long getSessionMaxReadPackageId(String appKey, IdentityType identityType, String from, Byte deviceType, String to) {
        // 先从redis 中获取

        String sessionMessageOffsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(appKey, identityType.value(), from, deviceType, to);
        Long sessionMessageOffset = (Long) redisTemplate.opsForValue().get(sessionMessageOffsetKey);
        if (sessionMessageOffset != null) {
            return sessionMessageOffset;
        }
        // 获取不到在从mongo 获取
        SessionMessageOffsetEntity mongoSessionMessageOffsetEntity = mongoTemplate.findOne(new Query(Criteria.where(SessionMessageOffsetEntity.Fields.from).is(from).and(SessionMessageOffsetEntity.Fields.to).is(to).and(SessionMessageOffsetEntity.Fields.type).is(identityType.value()).and(SessionMessageOffsetEntity.Fields.deviceType).is(deviceType)).limit(NumberConstant.NUMBER_1), SessionMessageOffsetEntity.class);
        if (mongoSessionMessageOffsetEntity != null) {
            return mongoSessionMessageOffsetEntity.getSessionMessageOffset();
        }
        // 最后在从数据库获取
        try {
            SessionMessageOffsetEntity sessionMessageOffsetEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_SESSION_MESSAGE_OFFSET.sql())
                    .param(SessionMessageOffsetEntity.Fields.from, from)
                    .param(SessionMessageOffsetEntity.Fields.to, to)
                    .param(SessionMessageOffsetEntity.Fields.type, identityType.value())
                    .param(SessionMessageOffsetEntity.Fields.deviceType, deviceType)
                    .query(SessionMessageOffsetEntity.class)
                    .single();
            Long maxSessionMessageOffset = sessionMessageOffsetEntity.getSessionMessageOffset();
            // 缓存会话最大已读id
            if (maxSessionMessageOffset != null) {
                // 注意：这里没有放mongo,没必要了
                redisTemplate.opsForValue().set(sessionMessageOffsetKey, maxSessionMessageOffset);
            }
            return maxSessionMessageOffset;
        }catch (EmptyResultDataAccessException e) {
            log.error("sessionMessageOffsetEntity 不存在,  from: {}, to:{}, type:{} , 原因: {}", from, to, identityType, e.getMessage());
            return null;
        } catch (Exception e) {
            log.error("获取会话偏移量实体异常, from: {}, to:{}, type:{} 原因：{}", from, to, identityType, e.getMessage());
            return null;
        }
    }


    /**
     * 验证特殊消息，校验通过返回true, 不通过返回false
     * @param packet
     * @param sessionId
     * @return
     */
    @SuppressWarnings("unchecked")
    private Mono<Boolean> reactiveValidSpecialMessage(Packet packet, String sessionId, Function<List<Packet>, Mono<Boolean>> function, Predicate<List<Packet>> extraPredicate) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        List<Long> packetIds;
        try {
            packetIds = JSON.parseArray(message.getContent(), Long.class);
        } catch (Exception e) {
            log.error("解析消息内容失败", e);
            return Mono.just(false);
        }
        // 如果没有消息id，则直接返回false
        if (CollectionUtils.isEmpty(packetIds) || packetIds.size() > MessageConstant.MAX_HANDLE_MESSAGE_COUNT) {
            log.error("消息数量为0或超出限制 {}!", MessageConstant.MAX_HANDLE_MESSAGE_COUNT);
            return Mono.just(false);
        }
        // 获取需要消息服务端时间戳，这个获取要在会话锁的前提下获取,注意批量获取score 的方法是redis 6.2.0 之后的版本才支持,如果不支持请使用其他方式替换，或升级redis版本，这里 就使用lua 脚本 哈哈哈
        // 获取消息在会话中的消息服务端时间戳
        Flux<Long> scoreFlux = reactiveRedisTemplate.execute(new DefaultRedisScript<>(LuaScriptEnum.BATCH_SCORE_LUA_SCRIPT.getScript(), List.class), List.of(CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId)), packetIds)
                .cache(); // 关键：缓存结果避免重复订阅;
        return scoreFlux
                .collectList() // 转换为Mono<List<Long>>
                .flatMap(scores -> {
                    // 4.1 校验非空结果数量
                    if (CollectionUtils.isEmpty(scores) || scores.stream().filter(Objects::nonNull).count() != packetIds.size()) {
                        log.error("会话:{} 不存在该消息id: {}, 或消息id数量与会话中的消息数量不相等", sessionId, packetIds);
                        return Mono.just(false);
                    }
                    // 4.2 获取持久化消息（假设已改造为响应式方法）
                    return fetchPacketsReactive(metadata.getAppKey(), packetIds)
                            .flatMap(packets -> {
                                if (packets.size() != packetIds.size()) {
                                    log.error("持久化消息数量不匹配 | session={} | expected={} | actual={}",
                                            sessionId, packetIds.size(), packets.size());
                                    return Mono.just(false);
                                }
                                return function.apply(packets).flatMap(valid -> {
                                    if (!valid) return Mono.just(false);
                                    // 若有额外条件，执行验证
                                    if (extraPredicate != null) {
                                        if (!extraPredicate.test(packets)) {
                                            return Mono.just(false);
                                        }
                                    }
                                    return Mono.just(true);
                                });
                            });
                })
                .onErrorResume(e -> {
                    log.error("消息处理异常 | session={}", sessionId, e);
                    return Mono.just(false);
                });
    }

    /**
     * 响应式获取持久化消息
     * @param appKey
     * @param packetIds
     * @return
     */
    private Mono<List<Packet>> fetchPacketsReactive(String appKey, List<Long> packetIds) {
        return Mono.fromCallable(() -> getPackets(appKey, packetIds))
                .subscribeOn(Schedulers.boundedElastic()); // 阻塞操作在弹性线程池
    }







    /**
     * 响应式处理读已回执消息
     */
    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveReadReceiptMessage(Packet packet, IdentityType identityType,  long expireTime) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        Metadata metadata = message.getMetadata();
        // 已读的消息id, 一般建议只传递一个数据，如果传递多个则取最大的一个
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        Long maxReadPacketId = null;
        if (CollectionUtils.isNotEmpty(readPacketIds)) {
            maxReadPacketId = readPacketIds.stream().max(Comparator.comparingLong(Long::longValue)).orElse(null);
        }
        if (maxReadPacketId == null) {
            log.error("已读的消息id不能为空 | packet={}", packet);
            return Mono.just(false);
        }
        return reactiveRedisTemplate.opsForValue().set(CacheConstant.buildSessionReadMessageOffsetCacheKey(metadata.getAppKey(), identityType.value(), from, packet.getDeviceType(), to), maxReadPacketId, Duration.ofMillis(expireTime));
    }




    /**
     * 获取群组用户列表的用户唯一标识，这里直接从缓存中取，获取不到就失败，不需要再从数据库中获取，如果有需要可以做多级缓存
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // score 存储的是用户加入群的时间戳，毫秒
        return stringRedisTemplate.opsForZSet().range(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), message.getTo()), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }


    /**
     * 获取群成员列表信息（同步版本，保留兼容性）
     *
     * @return
     */
    public Set<GroupUserEntity> groupUserEntitySet(String appKey, String groupId, Set<String> memberIdSet) {
        if (CollectionUtils.isEmpty(memberIdSet)) {
            return Set.of();
        }

        Set<String> candidateMemberIds = memberIdSet.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (CollectionUtils.isEmpty(candidateMemberIds)) {
            return Set.of();
        }

        Map<String, GroupUserEntity> resultMap = new HashMap<>();
        Map<String, String> memberIdCacheKeyMap = new HashMap<>();
        Map<String, String> cacheKeyMemberIdMap = new HashMap<>();
        for (String memberId : candidateMemberIds) {
            String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);
            memberIdCacheKeyMap.put(memberId, cacheKey);
            cacheKeyMemberIdMap.put(cacheKey, memberId);
        }

        // 1. 本地缓存（批量获取）
        Set<String> cacheKeys = new LinkedHashSet<>(memberIdCacheKeyMap.values());
        Map<String, GroupUserEntity> localCacheMap = new HashMap<>();
        Map<String, GroupUserEntity> initialLocalHits = MessageContext.groupUserEntityCache.getAllMap(cacheKeys);
        if (initialLocalHits != null) {
            localCacheMap.putAll(initialLocalHits);
        }
        Set<String> pendingCacheKeys = new LinkedHashSet<>(cacheKeys);
        if (!localCacheMap.isEmpty()) {
            for (Map.Entry<String, GroupUserEntity> entry : localCacheMap.entrySet()) {
                GroupUserEntity entity = entry.getValue();
                if (entity == null) {
                    continue;
                }
                String cacheKey = entry.getKey();
                String memberId = cacheKeyMemberIdMap.get(cacheKey);
                if (memberId != null) {
                    resultMap.put(memberId, entity);
                    pendingCacheKeys.remove(cacheKey);
                }
            }
        }

        if (pendingCacheKeys.isEmpty()) {
            return new HashSet<>(resultMap.values());
        }

        // 2. Redis缓存
        List<String> redisCacheKeys = new ArrayList<>(pendingCacheKeys);
        if (CollectionUtils.isNotEmpty(redisCacheKeys)) {
            List<Object> redisEntities = redisTemplate.opsForValue().multiGet(redisCacheKeys);
            if (CollectionUtils.isNotEmpty(redisEntities)) {
                for (int i = 0; i < redisEntities.size(); i++) {
                    GroupUserEntity redisEntity = (GroupUserEntity) redisEntities.get(i);
                    String cacheKey = redisCacheKeys.get(i);
                    String memberId = cacheKeyMemberIdMap.get(cacheKey);
                    if (memberId != null && redisEntity != null) {
                        updateGroupUserCache(cacheKey, redisEntity);
                        resultMap.put(memberId, redisEntity);
                        pendingCacheKeys.remove(cacheKey);
                    }
                }
            }
        }

        if (pendingCacheKeys.isEmpty()) {
            return new HashSet<>(resultMap.values());
        }

        Set<String> missingMemberIds = pendingCacheKeys.stream()
                .map(cacheKeyMemberIdMap::get)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));

        if (CollectionUtils.isEmpty(missingMemberIds)) {
            return new HashSet<>(resultMap.values());
        }

        // 3. MongoDB + MySQL 兜底
        fetchGroupUsersFromLowerTiersSync(appKey, groupId, missingMemberIds, memberIdCacheKeyMap, resultMap);

        return new HashSet<>(resultMap.values());
    }

    /**
     * 响应式获取群成员列表信息（优化版本：支持多级缓存、非阻塞）
     *
     * @param appKey
     * @param groupId
     * @param memberIdSet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<Set<GroupUserEntity>> groupUserEntitySetReactive(String appKey, String groupId, Set<String> memberIdSet) {
        if (CollectionUtils.isEmpty(memberIdSet)) {
            return Mono.just(Set.of());
        }
        
        Map<String, String> cacheKeyMemberIdMap = memberIdSet.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(
                        memberId -> CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId),
                        Function.identity(),
                        (existing, replacement) -> existing,
                        LinkedHashMap::new));

        Set<String> cacheKeys = new LinkedHashSet<>(cacheKeyMemberIdMap.keySet());

        // 1. 先检查本地缓存（批量获取）
        Map<String, GroupUserEntity> localCacheMap = new HashMap<>();
        Map<String, GroupUserEntity> initialLocalMap = MessageContext.groupUserEntityCache.getAllMap(cacheKeys);
        if (initialLocalMap != null) {
            localCacheMap.putAll(initialLocalMap);
        }
        Set<String> remainingCacheKeys = new LinkedHashSet<>(cacheKeys);
        if (!localCacheMap.isEmpty()) {
            remainingCacheKeys.removeAll(localCacheMap.keySet());
        }

        // 全部命中本地缓存
        if (remainingCacheKeys.isEmpty()) {
            return Mono.just(new HashSet<>(localCacheMap.values()));
        }

        // 2. 从 Redis 批量获取（响应式）
        List<String> redisKeys = new ArrayList<>(remainingCacheKeys);
        Set<String> pendingCacheKeys = new LinkedHashSet<>(remainingCacheKeys);
        return Flux.fromIterable(redisKeys)
                .concatMap(key -> reactiveRedisTemplate.opsForValue().get(key)
                        .cast(GroupUserEntity.class)
                        .doOnNext(entity -> {
                            if (entity != null) {
                                MessageContext.groupUserEntityCache.put(key, (GroupUserEntity) entity);
                                localCacheMap.put(key, (GroupUserEntity) entity);
                                pendingCacheKeys.remove(key);
                            }
                        })
                        .onErrorResume(e -> {
                            log.warn("从Redis获取群成员异常, cacheKey: {}", key, e);
                            return Mono.empty();
                        }))
                .then(Mono.defer(() -> {
                    if (pendingCacheKeys.isEmpty()) {
                        return Mono.just(new HashSet<>(localCacheMap.values()));
                    }

                    Set<String> remainingMemberIds = pendingCacheKeys.stream()
                            .map(cacheKeyMemberIdMap::get)
                            .filter(Objects::nonNull)
                            .collect(Collectors.toCollection(LinkedHashSet::new));

                    if (CollectionUtils.isEmpty(remainingMemberIds)) {
                        return Mono.just(new HashSet<>(localCacheMap.values()));
                    }

                    return fetchGroupUsersFromLowerTiers(appKey, groupId, remainingMemberIds, new HashSet<>(localCacheMap.values()));
                }))
                .onErrorResume(e -> {
                    log.error("响应式获取群成员列表异常, appKey: {}, groupId: {}", appKey, groupId, e);
                    return Mono.just(Set.<GroupUserEntity>of());
                });
    }



    /**
     * 获取群成员信息（同步版本，多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param groupId
     * @param memberId
     * @return
     */
    public GroupUserEntity groupUserEntity(String appKey, String groupId, String memberId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);
        
        // 1. 本地缓存
        GroupUserEntity groupUserEntity = MessageContext.groupUserEntityCache.get(cacheKey);
        if (groupUserEntity != null) {
            return groupUserEntity;
        }
        
        // 2. Redis缓存
        groupUserEntity = (GroupUserEntity) redisTemplate.opsForValue().get(cacheKey);
        if (groupUserEntity != null) {
            updateGroupUserCache(cacheKey, groupUserEntity);
            return groupUserEntity;
        }
        
        // 3. MongoDB
        try {
            MongoGroupUserEntity mongoGroupUser = mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                            .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId))),
                    MongoGroupUserEntity.class);
            if (mongoGroupUser != null) {
                groupUserEntity = convertMongoGroupUserToGroupUser(mongoGroupUser);
                updateGroupUserCache(cacheKey, groupUserEntity);
                return groupUserEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
        }
        
        // 4. MySQL
        return queryGroupUserEntityFromDataBase(cacheKey, appKey, groupId, memberId);
    }


    /**
     * 从数据库查询群成员信息
     * @param appKey
     * @param groupId
     * @param memberId
     * @return
     */
    private GroupUserEntity queryGroupUserEntityFromDataBase(String cacheKey, String appKey, String groupId, String memberId) {
        try {
            GroupUserEntity groupUserEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_GROUP_USER.sql())
                    .param(GroupUserEntity.Fields.userId, memberId)
                    .param(GroupUserEntity.Fields.groupId, groupId)
                    .query(GroupUserEntity.class)
                    .optional()
                    .orElse(null);
            if (groupUserEntity != null) {
                updateGroupUserCache(cacheKey, groupUserEntity);
            }
            return groupUserEntity;
        } catch (Exception e) {
            log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
            return null;
        }
    }

    /**
     * 响应式获取群成员信息（多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param groupId
     * @param memberId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<GroupUserEntity> groupUserEntityReactive(String appKey, String groupId, String memberId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, memberId, groupId);
        
        // 1. 本地缓存
        GroupUserEntity localCached = MessageContext.groupUserEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }
        
        // 2. Redis缓存（响应式）
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(GroupUserEntity.class)
                .doOnNext((Object groupUserEntity) -> {
                    if (groupUserEntity != null) {
                        updateGroupUserCache(cacheKey, (GroupUserEntity) groupUserEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        reactiveMongoTemplate.findOne(
                                Query.query(Criteria.where(MongoGroupUserEntity.Fields.userId).is(Long.parseLong(memberId))
                                        .and(MongoGroupUserEntity.Fields.groupId).is(Long.parseLong(groupId))),
                                MongoGroupUserEntity.class)
                                .map(this::convertMongoGroupUserToGroupUser)
                                .doOnNext(groupUserEntity -> updateGroupUserCache(cacheKey, groupUserEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        Mono.fromCallable(() -> {
                                            try {
                                                return jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_GROUP_USER.sql())
                                                        .param(GroupUserEntity.Fields.userId, memberId)
                                                        .param(GroupUserEntity.Fields.groupId, groupId)
                                                        .query(GroupUserEntity.class)
                                                        .optional()
                                                        .orElse(null);
                                            } catch (Exception e) {
                                                log.error("从MySQL查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                                                return null;
                                            }
                                        })
                                        .subscribeOn(Schedulers.fromExecutor(dbExecutor()))
                                        .doOnNext(groupUserEntity -> {
                                            if (groupUserEntity != null) {
                                                updateGroupUserCache(cacheKey, groupUserEntity);
                                            }
                                        })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群成员异常, appKey: {}, groupId: {}, memberId: {}", appKey, groupId, memberId, e);
                    return Mono.empty();
                });
    }

    /**
     * 更新群成员缓存（本地缓存和Redis）
     */
    private void updateGroupUserCache(String cacheKey, GroupUserEntity groupUserEntity) {
        if (groupUserEntity != null) {
            MessageContext.groupUserEntityCache.put(cacheKey, groupUserEntity);
            redisTemplate.opsForValue().set(cacheKey, groupUserEntity);
        }
    }

    /**
     * 转换MongoDB群成员实体为MySQL群成员实体
     */
    private GroupUserEntity convertMongoGroupUserToGroupUser(MongoGroupUserEntity mongoGroupUser) {
        if (mongoGroupUser == null) {
            return null;
        }
        GroupUserEntity groupUserEntity = new GroupUserEntity();
        groupUserEntity.setId(mongoGroupUser.getId());
        groupUserEntity.setGroupId(mongoGroupUser.getGroupId());
        groupUserEntity.setGroupCode(mongoGroupUser.getGroupCode());
        groupUserEntity.setGroupNickName(mongoGroupUser.getGroupNickName());
        groupUserEntity.setUserId(mongoGroupUser.getUserId());
        groupUserEntity.setUserCode(mongoGroupUser.getUserCode());
        groupUserEntity.setPost(mongoGroupUser.getPost());
        groupUserEntity.setSilence(mongoGroupUser.getSilence());
        groupUserEntity.setUserNickName(mongoGroupUser.getUserNickName());
        groupUserEntity.setShield(mongoGroupUser.getShield());
        groupUserEntity.setCreateTime(mongoGroupUser.getCreateTime());
        return groupUserEntity;
    }


    /**
     * 获取群管理员和群主的唯一标识
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupManagerAndLeaderUsersIdentity(Packet packet) {
        return stringRedisTemplate.opsForZSet().rangeByScore(CacheConstant.buildGroupUserCacheKey(packet.getMessage().getMetadata().getAppKey(), packet.getMessage().getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
    }
    /**
     * 获取群管理员和群主的唯一标识
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Map<String, Double> groupManagerAndLeaderUsersIdentityAndPost(Packet packet) {
        Map<String, Double>  groupManagerAndLeaderUsersIdentityAndPost = new HashMap<>();
        Set<ZSetOperations.TypedTuple<String>> tuples = stringRedisTemplate.opsForZSet().rangeByScoreWithScores(CacheConstant.buildGroupUserCacheKey(packet.getMessage().getMetadata().getAppKey(), packet.getMessage().getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
        if (tuples != null && !tuples.isEmpty()) {
            for (ZSetOperations.TypedTuple<String> tuple : tuples) {
                groupManagerAndLeaderUsersIdentityAndPost.put(tuple.getValue(), tuple.getScore());
            }
        }
        return groupManagerAndLeaderUsersIdentityAndPost;
    }

    /**
     * 获取群管理员和群主的实体配置信息
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<GroupUserEntity> groupManagerAndLeaderUserEntity(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Set<String> groupManagerAndLeaderUsersIdentitySet = stringRedisTemplate.opsForZSet().rangeByScore(CacheConstant.buildGroupUserCacheKey(appKey, message.getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
        if (CollectionUtils.isEmpty(groupManagerAndLeaderUsersIdentitySet)) {
            log.error("群：{} 不存在群主和群成员", message.getTo());
            return Set.of();
        }
        return groupUserEntitySet(appKey, message.getTo(), groupManagerAndLeaderUsersIdentitySet);
    }

    /**
     * 获取群管理员的唯一标识
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupManagerUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        return stringRedisTemplate.opsForZSet().range(CacheConstant.buildGroupUserCacheKey(appKey, message.getTo()), GroupUserPost.MANAGER.value(), GroupUserPost.MANAGER.value());
    }

    /**
     * 获取群主的唯一标识
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public String groupLeaderUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        Set<String> leanderIdentitySet = stringRedisTemplate.opsForZSet().range(CacheConstant.buildGroupUserCacheKey(appKey, message.getTo()), GroupUserPost.LEADER.value(), GroupUserPost.LEADER.value());
        if (CollectionUtils.isEmpty(leanderIdentitySet)) {
            log.warn("群 {} 中不存在管理员", message.getTo());
            return null;
        }
        return leanderIdentitySet.stream().findFirst().orElse(null);
    }



    /**
     * 保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public Mono<Boolean> reactiveSaveMessage(Packet packet, String sessionId, long expireTime, boolean saveOfflineMessage, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        List<String> offlineKeys = toSupportDeviceTypes.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), message.getTo(), deviceType.getDeviceTypeValue())).toList();
        // 使用Mono.fromCallable将阻塞操作包装为响应式流
        return Mono.fromCallable(() -> saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId), saveOfflineMessage, offlineKeys, (ops) -> {}, (ops, msg, app, f, t) -> {}))
                // 指定在弹性线程池中执行阻塞操作，避免阻塞Netty事件循环
                .subscribeOn(Schedulers.boundedElastic())
                // 响应式错误处理
                .onErrorResume(e -> {
                    log.error("Reactive save message failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }


    /**
     * 保存业务消息以及离线消息和会话消息
     * @param packet
     * @param to 接受者
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<Boolean> reactiveSaveOfflineMessage(Packet packet, String to, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String packetId = SnowflakeUtil.formatLong(packet.getPacketId()); // 转换为字符串
        double score = NumberConstant.NUMBER_0; // 分数
        RedisSerializer<String> stringSerializer = RedisSerializer.string();

        // 适配返回Flux<Boolean>的execute方法
        Flux<Boolean> executeResult = reactiveRedisTemplate.execute((ReactiveRedisCallback<Boolean>) connection -> {
            List<Mono<Boolean>> operations = new ArrayList<>();

            for (DeviceType deviceType : toSupportDeviceTypes) {
                byte[] keyBytes = stringSerializer.serialize(CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), to, deviceType.getDeviceTypeValue()));
                byte[] valueBytes = stringSerializer.serialize(packetId);

                if (keyBytes == null || valueBytes == null) {
                    operations.add(Mono.just(false));
                    continue;
                }

                Mono<Boolean> addOperation = connection.zSetCommands()
                        .zAdd(ByteBuffer.wrap(keyBytes), score, ByteBuffer.wrap(valueBytes))
                        .map(count -> count > 0);

                operations.add(addOperation);
            }

            // 返回Flux<Boolean>，每个元素是单个操作的结果
            return Flux.fromIterable(operations).concatMap(mono -> mono);
        });

        // 关键：将Flux<Boolean>转换为Mono<Boolean>，判断所有结果是否都为true
        return executeResult.collectList()
                .map(results -> results.stream().allMatch(Boolean::booleanValue))
                .onErrorReturn(false);
    }


    /**
     * 保存加好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean saveJoinFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        // 获取原来存在的sessionid
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime,toSupportDeviceTypes,  (redisOperations)-> redisOperations.opsForValue().setIfAbsent(CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getFrom(), message.getTo()), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }


    /**
     * 获取加好友请求会话信息
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    public RequestSession getFriendRequestSession(String appKey, String from, String to) {
        return  (RequestSession) redisTemplate.opsForValue().get(CacheConstant.buildFriendRequestCacheKey(appKey, from, to));
    }

    /**
     * 获取好友请求会话信息
     * @param appKey
     * @param joiner
     * @param groupId
     * @return
     */
    public GroupRequestSession getGroupRequestSession(String appKey, String joiner, String groupId) {
        return  (GroupRequestSession) redisTemplate.opsForValue().get(CacheConstant.buildGroupRequestCacheKey(appKey, joiner, groupId));
    }

    /**
     * 保存拒绝好友请求,
     * @param packet
     * @param expireTime
     * @return
     */
    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession,  long expireTime, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, toSupportDeviceTypes, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getTo(), message.getFrom()), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 保存好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    private<K,V> boolean saveFriendRequestMessage(Packet packet, String friendRequestSessionId, long expireTime, Collection<DeviceType> toSupportDeviceTypes,Consumer<RedisOperations<K, V>> consumer) {
        // 调用公共方法，传入空的额外操作
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String from = message.getFrom();
        String to = message.getTo();
        List<String> offlineKeys = toSupportDeviceTypes.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(appKey, to, deviceType.getDeviceTypeValue())).toList();
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), true, offlineKeys, consumer, (ops, msg, ak, f, t) -> {});
    }




    /**
     * 群组批量保存，保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public Mono<Boolean> reactiveBatchSaveMessage(Packet packet, Map<String, Collection<DeviceType>>  groupUserIdentityDeviceTypeMap, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 构造参数
        List<String> offlineKeys = Lists.newArrayList();
        groupUserIdentityDeviceTypeMap.forEach((groupUserIdentity, deviceTypes) -> offlineKeys.addAll(deviceTypes.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), message.getTo(), deviceType.getDeviceTypeValue())).toList()));
        return Mono.fromCallable(() -> saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildSessionCacheKey(metadata.getAppKey(), message.getTo()), true, offlineKeys, (ops) -> {}, (ops, msg, app, f, t) -> {}))
                // 指定在弹性线程池中执行阻塞操作，避免阻塞Netty事件循环
                .subscribeOn(Schedulers.boundedElastic())
                // 响应式错误处理
                .onErrorResume(e -> {
                    log.error("Reactive save message failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }


    /**
     * 判断在appKey 下 from 和 to 是否是好友关系
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean isFriend(String appKey, String from, String to) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);
        // 1. 本地缓存
        FriendEntity friendEntity = MessageContext.friendEntityCache.get(cacheKey);
        if (friendEntity != null) {
            return true;
        }
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return stringRedisTemplate.opsForZSet().score(CacheConstant.buildFriendsCacheKey(appKey, from), to) != null;
    }

    /**
     * 判断在appKey 下 from 是否已经在群中
     * @param appKey
     * @param from
     * @param groupId
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean inGroup(String appKey, String from, String groupId) {
        String cacheKey = CacheConstant.buildGroupUserConfigCacheKey(appKey, from, groupId);
        // 1. 本地缓存
        GroupUserEntity groupUserEntity = MessageContext.groupUserEntityCache.get(cacheKey);
        if (groupUserEntity != null) {
            return true;
        }
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return stringRedisTemplate.opsForZSet().score(CacheConstant.buildGroupUserCacheKey(appKey, groupId), from) != null;
    }


    /**
     * 获取在appKey 下 from 的所有好友
     * @return
     */
    public Collection<String> getFriendIds(String appKey, String from) {
       return stringRedisTemplate.opsForZSet().range(CacheConstant.buildFriendsCacheKey(appKey, from), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }

    /**
     * 获取在appKey 下 from 和 to 的朋友关系（同步版本，多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    public FriendEntity getFriend(String appKey, String from, String to) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);
        
        // 1. 本地缓存
        FriendEntity friendEntity = MessageContext.friendEntityCache.get(cacheKey);
        if (friendEntity != null) {
            return friendEntity;
        }
        
        // 2. Redis缓存
        friendEntity = (FriendEntity) redisTemplate.opsForValue().get(cacheKey);
        if (friendEntity != null) {
            updateFriendCache(cacheKey, friendEntity);
            return friendEntity;
        }
        
        // 3. MongoDB
        try {
            MongoFriendEntity mongoFriend = mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoFriendEntity.Fields.userId).is(Long.parseLong(from))
                            .and(MongoFriendEntity.Fields.friendUserId).is(Long.parseLong(to))),
                    MongoFriendEntity.class);
            if (mongoFriend != null) {
                friendEntity = convertMongoFriendToFriend(mongoFriend);
                updateFriendCache(cacheKey, friendEntity);
                return friendEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
        }
        
        // 4. MySQL
        try {
            friendEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_FRIEND.sql())
                    .param(FriendEntity.Fields.userId, from)
                    .param(FriendEntity.Fields.friendUserId, to)
                    .query(FriendEntity.class)
                    .optional()
                    .orElse(null);
            if (friendEntity != null) {
                updateFriendCache(cacheKey, friendEntity);
            }
        } catch (Exception e) {
            log.error("从MySQL查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
        }
        
        return friendEntity;
    }

    /**
     * 响应式获取好友关系（多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<FriendEntity> getFriendReactive(String appKey, String from, String to) {
        String cacheKey = CacheConstant.buildFriendsConfigCacheKey(appKey, from, to);
        
        // 1. 本地缓存
        FriendEntity localCached = MessageContext.friendEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }
        
        // 2. Redis缓存（响应式）
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(FriendEntity.class)
                .doOnNext((Object friendEntity) -> {
                    if (friendEntity != null) {
                        updateFriendCache(cacheKey, (FriendEntity) friendEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        reactiveMongoTemplate.findOne(
                                Query.query(Criteria.where(MongoFriendEntity.Fields.userId).is(Long.parseLong(from))
                                        .and(MongoFriendEntity.Fields.friendUserId).is(Long.parseLong(to))),
                                MongoFriendEntity.class)
                                .map(this::convertMongoFriendToFriend)
                                .doOnNext(friendEntity -> updateFriendCache(cacheKey, friendEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        Mono.fromCallable(() -> {
                                            try {
                                                return jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_FRIEND.sql())
                                                        .param(FriendEntity.Fields.userId, from)
                                                        .param(FriendEntity.Fields.friendUserId, to)
                                                        .query(FriendEntity.class)
                                                        .optional()
                                                        .orElse(null);
                                            } catch (Exception e) {
                                                log.error("从MySQL查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
                                                return null;
                                            }
                                        })
                                        .subscribeOn(Schedulers.fromExecutor(dbExecutor()))
                                        .doOnNext(friendEntity -> {
                                            if (friendEntity != null) {
                                                updateFriendCache(cacheKey, friendEntity);
                                            }
                                        })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询好友关系异常, appKey: {}, from: {}, to: {}", appKey, from, to, e);
                    return Mono.empty();
                });
    }

    /**
     * 更新好友缓存（本地缓存和Redis）
     */
    private void updateFriendCache(String cacheKey, FriendEntity friendEntity) {
        if (friendEntity != null) {
            MessageContext.friendEntityCache.put(cacheKey, friendEntity);
            redisTemplate.opsForValue().set(cacheKey, friendEntity);
        }
    }

    /**
     * 转换MongoDB好友实体为MySQL好友实体
     */
    private FriendEntity convertMongoFriendToFriend(MongoFriendEntity mongoFriend) {
        if (mongoFriend == null) {
            return null;
        }
        FriendEntity friendEntity = new FriendEntity();
        friendEntity.setId(mongoFriend.getId());
        friendEntity.setUserId(mongoFriend.getUserId());
        friendEntity.setFriendUserId(mongoFriend.getFriendUserId());
        friendEntity.setFriendUserCode(mongoFriend.getFriendUserCode());
        friendEntity.setFriendNickName(mongoFriend.getFriendNickName());
        friendEntity.setShield(mongoFriend.getShield());
        friendEntity.setCreateTime(mongoFriend.getCreateTime());
        friendEntity.setUpdateTime(mongoFriend.getUpdateTime());
        return friendEntity;
    }

    /**
     * 获取在appKey 下 from 和 to 的朋友关系（同步版本，多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param groupId
     * @return
     */
    public GroupEntity getGroupEntity(String appKey, String groupId) {
        String cacheKey = CacheConstant.buildGroupCacheKey(appKey, groupId);
        
        // 1. 本地缓存
        GroupEntity groupEntity = MessageContext.groupEntityCache.get(cacheKey);
        if (groupEntity != null) {
            return groupEntity;
        }
        
        // 2. Redis缓存
        groupEntity = (GroupEntity) redisTemplate.opsForValue().get(cacheKey);
        if (groupEntity != null) {
            updateGroupCache(cacheKey, groupEntity);
            return groupEntity;
        }
        
        // 3. MongoDB
        try {
            MongoGroupEntity mongoGroup = mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoGroupEntity.Fields.id).is(Long.parseLong(groupId))
                            .and(MongoGroupEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                    MongoGroupEntity.class);
            if (mongoGroup != null) {
                groupEntity = convertMongoGroupToGroup(mongoGroup);
                updateGroupCache(cacheKey, groupEntity);
                return groupEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
        }
        
        // 4. MySQL
        groupEntity = getGroupEntityFromDatabases(appKey, groupId);
        if (groupEntity != null) {
            updateGroupCache(cacheKey, groupEntity);
        }
        
        return groupEntity;
    }

    /**
     * 响应式获取群组实体（多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param groupId
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<GroupEntity> getGroupEntityReactive(String appKey, String groupId) {
        String cacheKey = CacheConstant.buildGroupCacheKey(appKey, groupId);
        
        // 1. 本地缓存
        GroupEntity localCached = MessageContext.groupEntityCache.get(cacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }
        
        // 2. Redis缓存（响应式）
        return reactiveRedisTemplate.opsForValue().get(cacheKey)
                .cast(GroupEntity.class)
                .doOnNext((Object groupEntity) -> {
                    if (groupEntity != null) {
                        updateGroupCache(cacheKey, (GroupEntity) groupEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        reactiveMongoTemplate.findOne(
                                Query.query(Criteria.where(MongoGroupEntity.Fields.id).is(Long.parseLong(groupId))
                                        .and(MongoGroupEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                                MongoGroupEntity.class)
                                .map(this::convertMongoGroupToGroup)
                                .doOnNext(groupEntity -> updateGroupCache(cacheKey, groupEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        getGroupEntityFromDatabasesReactive(appKey, groupId)
                                                .doOnNext(groupEntity -> {
                                                    if (groupEntity != null) {
                                                        updateGroupCache(cacheKey, groupEntity);
                                                    }
                                                })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询群组异常, appKey: {}, groupId: {}", appKey, groupId, e);
                    return Mono.empty();
                });
    }

    /**
     * 更新群组缓存（本地缓存和Redis）
     */
    private void updateGroupCache(String cacheKey, GroupEntity groupEntity) {
        if (groupEntity != null) {
            MessageContext.groupEntityCache.put(cacheKey, groupEntity);
            redisTemplate.opsForValue().set(cacheKey, groupEntity);
        }
    }

    /**
     * 转换MongoDB群组实体为MySQL群组实体
     */
    private GroupEntity convertMongoGroupToGroup(MongoGroupEntity mongoGroup) {
        if (mongoGroup == null) {
            return null;
        }
        GroupEntity groupEntity = new GroupEntity();
        groupEntity.setId(mongoGroup.getId());
        groupEntity.setGroupCode(mongoGroup.getGroupCode());
        groupEntity.setGroupName(mongoGroup.getGroupName());
        groupEntity.setGroupAvatar(mongoGroup.getGroupAvatar());
        groupEntity.setGroupDescription(mongoGroup.getGroupDescription());
        groupEntity.setGroupAnnouncement(mongoGroup.getGroupAnnouncement());
        groupEntity.setGroupJoinPolicy(mongoGroup.getGroupJoinPolicy());
        groupEntity.setStatus(mongoGroup.getStatus());
        groupEntity.setSilence(mongoGroup.getSilence());
        groupEntity.setAppKey(mongoGroup.getAppKey());
        groupEntity.setCreateTime(mongoGroup.getCreateTime());
        groupEntity.setUpdateTime(mongoGroup.getUpdateTime());
        groupEntity.setDeleted(mongoGroup.getDeleted());
        return groupEntity;
    }

    /**
     * 从数据库中获取群组实体
     * @param appKey
     * @param groupId
     * @return
     */
    public GroupEntity getGroupEntityFromDatabases(String appKey, String groupId) {
        try {
            log.info("从数据库中获取群组实体, groupId: {}", groupId);
            GroupEntity groupEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_GROUP.sql())
                    .params(groupId)
                    .query(GroupEntity.class)
                    .single();
            // 走不到这里就会进异常
            redisTemplate.opsForValue().set(CacheConstant.buildGroupCacheKey(appKey, groupId), groupEntity);
            return groupEntity;
        } catch (EmptyResultDataAccessException e) {
            log.warn("群组不存在, groupId: {}", groupId);
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("同一个groupId存在多个群组, groupId: {}", groupId);
            throw new RuntimeException("同一个groupIdy存在多个用于, groupId: " + groupId);
        }catch (Exception e) {
            log.error("获取群组实体异常, groupId: {}, 原因：{}", groupId, e.getMessage());
            throw new RuntimeException("获取群组实体异常, groupId: " + groupId);
        }
    }

    /**
     * 响应式封装：将同步数据库查询转换为 Mono<GroupEntity>
     * 核心：通过 publishOn 切换到专用线程池执行同步任务，避免阻塞核心线程
     */
    public Mono<GroupEntity> getGroupEntityFromDatabasesReactive(String appKey, String groupId) {
        // 1. 入参校验（提前拦截无效请求，避免线程池资源浪费）
        if (StringUtils.isBlank(appKey) || StringUtils.isBlank(groupId)) {
            log.warn("响应式查询群组：appKey 或 groupId 为空，appKey:{}, groupId:{}", appKey, groupId);
            return Mono.empty(); // 空参数返回空流
        }

        // 2. 将同步方法封装为 Supplier（供给型函数，无参有返回值）
        // 注意：Supplier 中的逻辑会在 publishOn 指定的线程池中执行
        return Mono.fromSupplier(() -> getGroupEntityFromDatabases(appKey, groupId))
                // 3. 切换到专用线程池执行同步任务（关键：避免阻塞 Reactor 核心线程）
                .publishOn(Schedulers.fromExecutor(dbExecutor()))
                // 4. 响应式异常处理：将同步方法抛出的 RuntimeException 转换为响应式错误信号
                .onErrorResume(e -> {
                    log.error("响应式查询群组异常, appKey:{}, groupId:{}", appKey, groupId, e);
                    // 返回错误信号，上游可通过 onError 捕获
                    return Mono.error(new RuntimeException("响应式查询群组失败, groupId: " + groupId, e));
                })
                // 5. 日志记录：打印响应式流的结果（可选，用于调试）
                .doOnSuccess(groupEntity -> {
                    if (groupEntity == null) {
                        log.debug("响应式查询群组：未找到群组, appKey:{}, groupId:{}", appKey, groupId);
                    } else {
                        log.debug("响应式查询群组：成功获取群组, appKey:{}, groupId:{}, 状态:{}",
                                appKey, groupId, groupEntity.getStatus());
                    }
                });
    }

    /**
     * 获取用户实体（同步版本，多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param identity 用户ID
     * @return
     */
    @SuppressWarnings("unchecked")
    public UserEntity getUserEntity(String appKey, String identity) {
        String userCacheKey = CacheConstant.buildUserCacheKey(appKey, identity);
        
        // 1. 本地缓存
        UserEntity userEntity = MessageContext.userEntityCache.get(userCacheKey);
        if (userEntity != null) {
            return userEntity;
        }
        
        // 2. Redis缓存
        userEntity = (UserEntity) redisTemplate.opsForValue().get(userCacheKey);
        if (userEntity != null) {
            updateUserCache(userCacheKey, userEntity);
            return userEntity;
        }
        
        // 3. MongoDB
        try {
            MongoUserEntity mongoUser = mongoTemplate.findOne(
                    Query.query(Criteria.where(MongoUserEntity.Fields.id).is(Long.parseLong(identity))
                            .and(MongoUserEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                    MongoUserEntity.class);
            if (mongoUser != null) {
                userEntity = convertMongoUserToUser(mongoUser);
                updateUserCache(userCacheKey, userEntity);
                return userEntity;
            }
        } catch (Exception e) {
            log.warn("从MongoDB查询用户异常, appKey: {}, identity: {}", appKey, identity, e);
        }
        
        // 4. MySQL
        try {
            log.info("从数据库中获取用户实体, identity: {}", identity);
            userEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_USER.sql())
                    .params(identity)
                    .query(UserEntity.class)
                    .single();
            // 存到缓存中,30天
            updateUserCache(userCacheKey, userEntity);
            return userEntity;
        } catch (EmptyResultDataAccessException e) {
            log.warn("用户不存在, identity: {}", identity);
            return null;
        } catch (IncorrectResultSizeDataAccessException e) {
            log.error("同一个identity存在多个用户, identity: {}", identity);
            throw new RuntimeException("同一个identity存在多个用于, identity: " + identity);
        }catch (Exception e) {
            log.error("获取用户实体异常, identity: {}, 原因：{}", identity, e.getMessage());
            throw new RuntimeException("获取用户实体异常, identity: " + identity);
        }
    }

    /**
     * 响应式获取用户实体（多级缓存：本地缓存 -> Redis -> MongoDB -> MySQL）
     * @param appKey
     * @param identity 用户ID
     * @return
     */
    @SuppressWarnings("unchecked")
    public Mono<UserEntity> getUserEntityReactive(String appKey, String identity) {
        String userCacheKey = CacheConstant.buildUserCacheKey(appKey, identity);
        
        // 1. 本地缓存
        UserEntity localCached = MessageContext.userEntityCache.get(userCacheKey);
        if (localCached != null) {
            return Mono.just(localCached);
        }
        
        // 2. Redis缓存（响应式）
        return reactiveRedisTemplate.opsForValue().get(userCacheKey)
                .cast(UserEntity.class)
                .doOnNext((Object userEntity) -> {
                    if (userEntity != null) {
                        updateUserCache(userCacheKey, (UserEntity) userEntity);
                    }
                })
                .switchIfEmpty(
                        // 3. MongoDB（响应式）
                        reactiveMongoTemplate.findOne(
                                Query.query(Criteria.where(MongoUserEntity.Fields.id).is(Long.parseLong(identity))
                                        .and(MongoUserEntity.Fields.deleted).is(NumberConstant.NUMBER_0)),
                                MongoUserEntity.class)
                                .map(this::convertMongoUserToUser)
                                .doOnNext(userEntity -> updateUserCache(userCacheKey, userEntity))
                                .switchIfEmpty(
                                        // 4. MySQL（响应式）
                                        Mono.fromCallable(() -> {
                                            try {
                                                return jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_USER.sql())
                                                        .params(identity)
                                                        .query(UserEntity.class)
                                                        .single();
                                            } catch (EmptyResultDataAccessException e) {
                                                log.warn("用户不存在, identity: {}", identity);
                                                return null;
                                            } catch (Exception e) {
                                                log.error("从MySQL查询用户异常, appKey: {}, identity: {}", appKey, identity, e);
                                                return null;
                                            }
                                        })
                                        .subscribeOn(Schedulers.fromExecutor(dbExecutor()))
                                        .doOnNext(userEntity -> {
                                            if (userEntity != null) {
                                                updateUserCache(userCacheKey, userEntity);
                                            }
                                        })
                                )
                )
                .onErrorResume(e -> {
                    log.error("响应式查询用户异常, appKey: {}, identity: {}", appKey, identity, e);
                    return Mono.empty();
                });
    }

    /**
     * 更新用户缓存（本地缓存和Redis）
     */
    private void updateUserCache(String cacheKey, UserEntity userEntity) {
        if (userEntity != null) {
            MessageContext.userEntityCache.put(cacheKey, userEntity);
            redisTemplate.opsForValue().set(cacheKey, userEntity, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP, TimeUnit.MILLISECONDS);
        }
    }

    /**
     * 转换MongoDB用户实体为MySQL用户实体
     */
    private UserEntity convertMongoUserToUser(MongoUserEntity mongoUser) {
        if (mongoUser == null) {
            return null;
        }
        UserEntity userEntity = new UserEntity();
        userEntity.setId(mongoUser.getId());
        userEntity.setOpenId(mongoUser.getOpenId());
        userEntity.setCode(mongoUser.getCode());
        userEntity.setUsername(mongoUser.getUsername());
        userEntity.setPassword(mongoUser.getPassword());
        userEntity.setNickName(mongoUser.getNickName());
        userEntity.setAvatar(mongoUser.getAvatar());
        userEntity.setMotto(mongoUser.getMotto());
        userEntity.setAge(mongoUser.getAge());
        userEntity.setSex(mongoUser.getSex());
        userEntity.setEmail(mongoUser.getEmail());
        userEntity.setPhoneNum(mongoUser.getPhoneNum());
        userEntity.setIdCardNo(mongoUser.getIdCardNo());
        userEntity.setGroupInvitePolicy(mongoUser.getGroupInvitePolicy());
        userEntity.setFriendJoinPolicy(mongoUser.getFriendJoinPolicy());
        userEntity.setStatus(mongoUser.getStatus());
        userEntity.setAppKey(mongoUser.getAppKey());
        userEntity.setRobot(mongoUser.getRobot());
        userEntity.setCreateTime(mongoUser.getCreateTime());
        userEntity.setUpdateTime(mongoUser.getUpdateTime());
        userEntity.setDeleted(mongoUser.getDeleted());
        return userEntity;
    }


    /**
     * 自动通过绑定好友关系，在缓存中
     * @param packet
     * @return
     */
    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession,  long expireTime, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        // 获取是否存在sessionId
        // 注意过期时间的设定，与消息 hot key 的过期时间保持一致
        return bindFriend(packet, requestSession.getSessionId(), expireTime, toSupportDeviceTypes, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildFriendRequestCacheKey(appKey, message.getFrom(), message.getTo()), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 同意绑定好友关系，在缓存中
     * @param appKey
     * @param packet
     * @return
     */
    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime, Collection<DeviceType> toSupportDeviceTypes) {
        Message message = packet.getMessage();
        // 注意过期时间的设定，与消息 hot key 的过期时间保持一致
        return bindFriend(packet, requestSession.getSessionId(), expireTime, toSupportDeviceTypes, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildFriendRequestCacheKey(appKey, message.getTo(), message.getFrom()), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }


    /**
     * 绑定好友关系，在缓存中
     * @param packet
     * @param expireTime
     * @param consumer
     * @return
     */
    private<K,V> boolean bindFriend(Packet packet, String friendRequestSessionId, long expireTime, Collection<DeviceType>  toSupportDeviceTypes, Consumer<RedisOperations<K, V>> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String from = message.getFrom();
        String to = message.getTo();
        String appKey = metadata.getAppKey();
        List<String> offlineKeys = toSupportDeviceTypes.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(appKey, to, deviceType.getDeviceTypeValue())).toList();
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), true, offlineKeys, consumer,
                (ops, msg, ak, f, t) -> {
                    // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
                    // 1. 强制转换为 RedisTemplate（获取连接的关键）
                    if (!(ops instanceof RedisTemplate)) {
                        throw new IllegalStateException("RedisOperations 不是 RedisTemplate 实例，无法获取连接");
                    }
                    RedisTemplate<K, V> redisTemplate = (RedisTemplate<K, V>) ops;
                    // 2. 获取 Redis 连接（通过 RedisTemplate 的底层方法）
                    RedisConnection conn = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
                    RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                    // 建立双向好友关系（仅bindFriend方法需要的逻辑）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    conn.zAdd(stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, from)), msg.getMetadata().getServerTime() , stringSerializer.serialize(t));
                    conn.zAdd(stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to)), msg.getMetadata().getServerTime(), stringSerializer.serialize(f));
                });
    }


    /**
     * 公共执行方法：提取重复逻辑，通过函数接口注入差异化操作
     * @param packet 消息包
     * @param expireTime 过期时间
     * @param consumer 自定义处理逻辑
     * @param extraOperation 额外操作（差异化逻辑）
     * @return 是否执行成功
     */
    @SuppressWarnings("unchecked")
    private<K, V> boolean saveMessageWithSessionOrOffline(Packet packet, long expireTime, String messageKey, String sessionKey, boolean saveOfflineMessage, List<String> offlineKeys, Consumer<RedisOperations<K, V>> consumer, FiveConsumer<RedisOperations<K, V>, Message, String, String, String> extraOperation) {
        try {
            Message message = packet.getMessage();
            Metadata metadata = message.getMetadata();
            String appKey = metadata.getAppKey();
            String from = message.getFrom();
            String to = message.getTo();
            String formatPacketId = SnowflakeUtil.formatLong(packet.getPacketId());

            // 获取字符串序列化器，增加灵活性
            RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
            // 执行Pipeline操作并获取结果
            redisTemplate.executePipelined(new SessionCallback<List<Object>>() {
                @Override
                @SuppressWarnings("unchecked")
                public <K1, V1> List<Object> execute(RedisOperations<K1, V1> operations) {
                    // 强制转换为明确的泛型类型，避免重复转换
                    // 1. 强制转换为 RedisTemplate（获取连接的关键）
                    if (!(operations instanceof RedisTemplate)) {
                        throw new IllegalStateException("RedisOperations 不是 RedisTemplate 实例，无法获取连接");
                    }
                    RedisTemplate redisTemplate = (RedisTemplate) operations;
                    // 2. 获取 Redis 连接（通过 RedisTemplate 的底层方法）
                    RedisConnection conn = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();

                    // 获取 Redis 底层连接（支持字节数组操作）
                    // 1. 保存消息主体
                    if (expireTime > 0) {
                        operations.opsForValue().set((K1) messageKey, (V1) packet, expireTime, TimeUnit.MILLISECONDS);
                    } else {
                        operations.opsForValue().set((K1) messageKey, (V1) packet);
                    }
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    byte[] formatPacketIdV = stringSerializer.serialize(formatPacketId);
                    // 保存请求会话消息
                    conn.zAdd(stringSerializer.serialize(sessionKey), NumberConstant.NUMBER_0, formatPacketIdV);
                    // 3. 条件保存离线消息
                    if (saveOfflineMessage && MessageContext.messageProperties.isQosEnable()  && message.getQos() > QosLevelEnum.QOS_0.getLevel() && CollectionUtils.isNotEmpty(offlineKeys)) {
                        for (String offlineKey : offlineKeys) {
                            conn.zAdd(stringSerializer.serialize(offlineKey), NumberConstant.NUMBER_0, formatPacketIdV);
                        }
                    }
                    // 4. 执行额外操作（差异化逻辑注入）
                    extraOperation.accept(redisTemplate, message, appKey, from, to);

                    // 5. 执行自定义处理逻辑
                    consumer.accept(redisTemplate);

                    return null;
                }
            });
            return true;
        } catch (Exception e) {
            log.error("执行消息操作失败: {}", e.getMessage(), e);
            return false;
        }
    }


    /**
     * 自动通过绑定群组关系
     * @return
     */
    public boolean autoPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 手动通过绑定群组关系
     * @return
     */
    public boolean manualPassBindGroup(Packet packet,  GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(),  expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }




    /**
     * 绑定好友关系，在缓存中
     * @param packet
     * @param expireTime
     * @param consumer
     * @return
     */
    @SuppressWarnings("unchecked")
    private<K,V> boolean bindGroup(Packet packet, String joiner, String groupId, String requestSessionId, long expireTime, Consumer<RedisOperations<K,V>> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), false, null, consumer, (ops, msg, ak, f, t) -> {
                    // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
                    // 1. 强制转换为 RedisTemplate（获取连接的关键）
                    if (!(ops instanceof RedisTemplate)) {
                        throw new IllegalStateException("RedisOperations 不是 RedisTemplate 实例，无法获取连接");
                    }
                    RedisTemplate redisTemplate = (RedisTemplate) ops;
                    // 2. 获取 Redis 连接（通过 RedisTemplate 的底层方法）
                    RedisConnection conn = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
                    RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                    // 建立双向好友关系（仅bindFriend方法需要的逻辑）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    conn.zAdd(stringSerializer.serialize(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), groupId)), GroupUserPost.ORDINARY.value() , stringSerializer.serialize(joiner));
                    conn.zAdd(stringSerializer.serialize(CacheConstant.buildUserGroupsCacheKey(metadata.getAppKey(), joiner)), msg.getMetadata().getServerTime(), stringSerializer.serialize(groupId));
                });
    }


    /**
     * 保存群组请求消息
     * @param packet
     * @param groupRequestSession
     * @param expireTime
     * @return
     */
    public boolean saveJoinGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().setIfAbsent(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 保存群组拒绝请求消息
     * @param packet
     * @param groupRequestSession
     * @param expireTime
     * @return
     */
    public boolean saveGroupRequestMessage(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }


    /**
     * 保存群组请求消息
     * @param packet
     * @param expireTime
     * @return
     */
    public<K,V> boolean saveGroupRequestMessage(Packet packet, String groupId, String requestSessionId, long expireTime, Consumer<RedisOperations<K,V>> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), false, null, consumer, (ops, msg, ak, f, t) -> {});
    }


    /**
     * 群组请求消息批量保存
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean batchSaveJoinGroupRequestMessage(Packet packet, Map<String, Collection<DeviceType>> groupUserIdentityDeviceTypeMap, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        List<String> offlineKeys = Lists.newArrayList();
        groupUserIdentityDeviceTypeMap.forEach((groupUserIdentity, deviceTypeCollection) -> {
            offlineKeys.addAll(deviceTypeCollection.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), groupUserIdentity, deviceType.getDeviceTypeValue())).toList());
        });
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId()), true,  offlineKeys, (redisOperations)-> redisOperations.opsForValue().setIfAbsent(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS), (ops, msg, ak, f, t) -> {});

    }

    /**
     * 群组请求拒绝消息批量保存
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean batchSaveGroupRequestMessage(Packet packet, Map<String, Collection<DeviceType>> groupUserIdentityDeviceTypeMap, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        List<String> offlineKeys = Lists.newArrayList();
        groupUserIdentityDeviceTypeMap.forEach((groupUserIdentity, deviceTypeCollection) -> {
            offlineKeys.addAll(deviceTypeCollection.stream().map(deviceType -> CacheConstant.buildOfflineCacheKey(metadata.getAppKey(), groupUserIdentity, deviceType.getDeviceTypeValue())).toList());
        });
        return saveMessageWithSessionOrOffline(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId()), true,  offlineKeys,  (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId()), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS), (ops, msg, ak, f, t) -> {});
    }



    /**
     * 设置最后一条消息为该会话
     * @param sessionId
     * @param lastPacket
     */
    @SuppressWarnings("unchecked")
    public void saveLastMessageForSession(String sessionId, Packet lastPacket, long expireTime, TimeUnit timeUnit) {
        // 获取会话中的最后一条消息id,这里不直接存packet，是为了节省redis 内存考虑，这里只存packetId
        redisTemplate.opsForValue().set(CacheConstant.buildSessionLastMessageCacheKey(lastPacket.getMessage().getMetadata().getAppKey(), sessionId), lastPacket.getPacketId(), expireTime, timeUnit);
    }
}
