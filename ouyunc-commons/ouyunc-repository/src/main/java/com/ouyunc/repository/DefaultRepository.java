package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.*;
import com.ouyunc.base.executor.ThreadPoolManager;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.constants.GroupUserPost;
import com.ouyunc.domain.constants.IdentityType;
import com.ouyunc.domain.entity.*;
import com.ouyunc.mq.kafka.KafkaFactory;
import com.ouyunc.repository.support.QosIdempotencyHelper;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.domain.Range;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.redis.connection.Limit;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.*;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

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
     * redisSerializer
     */
    private static final RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();

    /**
     * valueSerializer
     */
    private static final RedisSerializer<Object> valueSerializer = redisTemplate.getValueSerializer();

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
     * 检查 QoS 重发是否已处理：先 packetId 幂等键，再通道身份 + 客户端 messageId
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean checkDup(Packet packet, String channelLoginIdentity) {
        return QosIdempotencyHelper.isDuplicate(redisTemplate, packet, channelLoginIdentity);
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
        // 使用 List 而非 Set，保证 multiGet 顺序与 packetIds 一致，避免去重导致结果错位
        List<String> redisKeys = packetIds.stream()
                .map(id -> CacheConstant.buildMessageCacheKey(appKey, id))
                .collect(Collectors.toList());
        List<Packet> cachedPackets = (List<Packet>) redisTemplate.opsForValue().multiGet(redisKeys);
        if (cachedPackets == null) {
            log.warn("cachedPackets 为空, appKey={}", appKey);
            return Collections.emptyList();
        }
        // 过滤有效缓存并收集已存在的 ID
        Map<Long, Packet> cachedPacketMap = cachedPackets.stream()
                .filter(Objects::nonNull)
                .collect(Collectors.toMap(Packet::getPacketId, Function.identity(), (a, b) -> a));
        Set<Long> cachedIds = cachedPacketMap.keySet();

        // 全部命中缓存则直接返回
        if (cachedIds.size() == packetIds.size()) {
            return new ArrayList<>(cachedPacketMap.values());
        }

        // 2. 收集未命中缓存的 ID（使用 Set 提升 contains 性能 O(1)）
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
                                    List<MessageEntity> mysqlEntities = jdbcClient.sql(JdbcSqlDialectHolder.selectMessage())
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

    /**
     * 转换 MessageEntity 到 Packet
     */
    private List<Packet> convertToPackets(List<? extends MessageEntity> entities) {
        return entities.stream()
                .filter(Objects::nonNull)
                .map(this::convertToPacket)
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
            log.error("异步更新缓存失败, appKey={}, packetSize={}", appKey, dbPackets.size(), ex);
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
            return jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUserBatch())
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
            Message withdrawMsg = packet.getMessage();
            String appKey = withdrawMsg.getMetadata().getAppKey();
            String sessionCacheKey = CacheConstant.buildSessionCacheKey(appKey, sessionId);
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    for (Packet withdrawPacket : packets) {
                        withdrawPacket.setRetain(NumberConstant.NUMBER_1);
                        operations.opsForValue().set((K) CacheConstant.buildMessageCacheKey(appKey, withdrawPacket.getPacketId()), (V) withdrawPacket, MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                        String member = MessageContext.idGenerator().formatLongId19Str(withdrawPacket.getPacketId());
                        operations.opsForZSet().remove((K) sessionCacheKey, (V) member);
                    }
                    return null;
                }
            });
            if (isOneToOneSession(sessionId, withdrawMsg.getFrom(), withdrawMsg.getTo())) {
                int decr = 0;
                for (Packet withdrawPacket : packets) {
                    if (withdrawPacket == null || withdrawPacket.getMessage() == null) {
                        continue;
                    }
                    Message withdrawn = withdrawPacket.getMessage();
                    if (!withdrawMsg.getFrom().equals(withdrawn.getFrom())) {
                        continue;
                    }
                    if (isCountablePeerUnreadContentType(withdrawn.getContentType())) {
                        decr++;
                    }
                }
                if (decr > 0) {
                    decrSessionPeerUnread(appKey, withdrawMsg.getTo(), sessionId, decr,
                            MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP);
                }
            }
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
                                                 Consumer<MessageEvent> exceptionConsumer,
                                                 ExceptionCodeEnum exceptionCode) {
        return validator.flatMap(valid -> {
            if (!valid) {
                exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(exceptionCode, null, packet), MessageEventTypeEnum.EXCEPTION));
                return Mono.just(false);
            }
            // 优化后的代码片段，确保MQ发送成功后才执行processor
            return Mono.fromFuture(mqSender.get())
                    // 当MQ发送成功时，返回一个表示成功的Mono
                    .thenReturn(true)
                    .onErrorResume(ex -> {
                        // MQ发送失败时的处理
                        exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.MQ_PERSISTENCE_ERROR, ex.getMessage(), packet), MessageEventTypeEnum.EXCEPTION));
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
                            exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, "撤销或已读异常", packet), MessageEventTypeEnum.EXCEPTION));
                        }
                    })
                    .onErrorResume(ex -> {
                        log.error("操作处理异常 | packet={}", packet, ex);
                        exceptionConsumer.accept(new MessageEvent(ExceptionEventPayload.of(ExceptionCodeEnum.UNKNOWN_ERROR, ex.getMessage(), packet), MessageEventTypeEnum.EXCEPTION));
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
        Long sessionMessageOffset = null;
        Object cachedOffset = redisTemplate.opsForValue().get(sessionMessageOffsetKey);
        if (cachedOffset instanceof Number n) {
            sessionMessageOffset = n.longValue();
        }
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
            SessionMessageOffsetEntity sessionMessageOffsetEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectSessionMessageOffset())
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
                redisTemplate.opsForValue().set(sessionMessageOffsetKey, maxSessionMessageOffset,
                        MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
            }
            return maxSessionMessageOffset;
        }catch (EmptyResultDataAccessException e) {
            log.debug("sessionMessageOffsetEntity 不存在, from: {}, to: {}, type: {}", from, to, identityType);
            return null;
        } catch (Exception e) {
            log.error("获取会话偏移量实体异常, from: {}, to:{}, type:{} 原因：{}", from, to, identityType, e.getMessage());
            return null;
        }
    }

    /**
     * 已读校验/合并：同一用户在同一会话下多端游标取 max。
     * 性能优化：Pipeline 一次性获取所有设备的 Redis 偏移量，仅当 Redis 缺失时才回退到 Mongo/MySQL。
     */
    private long resolveMaxReadOffsetAllDevices(String appKey, IdentityType identityType, String from, String to) {
        List<Byte> deviceTypes = resolveDeviceTypeBytes(appKey);
        if (CollectionUtils.isEmpty(deviceTypes)) {
            return 0L;
        }

        // Step 1: Pipeline 批量从 Redis 获取所有设备 offset（1 次 RTT 替代 N 次）
        List<String> redisKeys = new ArrayList<>(deviceTypes.size());
        for (Byte deviceType : deviceTypes) {
            redisKeys.add(CacheConstant.buildSessionReadMessageOffsetCacheKey(appKey, identityType.value(), from, deviceType, to));
        }
        @SuppressWarnings("unchecked")
        List<Object> cached = redisTemplate.opsForValue().multiGet(redisKeys);

        long max = 0L;
        boolean found = false;
        // Step 2: 收集 Redis 命中数据，记录缺失的 deviceType
        List<Byte> missingDeviceTypes = new ArrayList<>();
        for (int i = 0; i < deviceTypes.size(); i++) {
            Object value = cached != null && i < cached.size() ? cached.get(i) : null;
            if (value instanceof Number offset) {
                found = true;
                max = Math.max(max, offset.longValue());
            } else {
                missingDeviceTypes.add(deviceTypes.get(i));
            }
        }

        // Step 3: 仅对 Redis 缺失的设备回退到 Mongo/MySQL（保持原行为兼容）
        for (Byte deviceType : missingDeviceTypes) {
            Long offset = getSessionMaxReadPackageId(appKey, identityType, from, deviceType, to);
            if (offset != null) {
                found = true;
                max = Math.max(max, offset);
            }
        }
        return found ? max : 0L;
    }

    /**
     * 与 im-service {@code getAllDeviceTypes} 一致：优先 Redis 配置的 appKey 设备类型，否则默认枚举。
     */
    @SuppressWarnings("unchecked")
    private List<Byte> resolveDeviceTypeBytes(String appKey) {
        Set<Object> configured = redisTemplate.opsForSet().members(CacheConstant.buildAppKeyDeviceTypeCacheKey(appKey));
        if (CollectionUtils.isNotEmpty(configured)) {
            List<Byte> types = new ArrayList<>();
            for (Object item : configured) {
                if (item instanceof DeviceType deviceType) {
                    types.add(deviceType.getType());
                }
            }
            if (!types.isEmpty()) {
                return types.stream().distinct().toList();
            }
        }
        return Arrays.stream(DeviceTypeEnum.values()).map(DeviceTypeEnum::getType).distinct().toList();
    }


    /**
     * 验证特殊消息，校验通过返回true, 不通过返回false
     * @param packet
     * @param sessionId
     * @return
     */
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
        // 会话 ZSet member 须与入库一致（19 位格式化 packetId）
        List<String> zsetMembers = packetIds.stream()
                .map(MessageContext.idGenerator()::formatLongId19Str)
                .toList();
        String sessionCacheKey = CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId);
        return Mono.fromCallable(() -> batchZSetScoresPipelined(sessionCacheKey, zsetMembers))
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap(scores -> {
                    int presentCount = countPresentZSetScores(scores);
                    if (scores.isEmpty() || presentCount != packetIds.size()) {
                        log.error("会话:{} 不存在该消息id: {}, 或消息id数量与会话中的消息数量不相等", sessionId, packetIds);
                        return Mono.just(false);
                    }
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
     * 管道批量 ZSCORE，一次 RTT；与 packetIds 等长，不存在为 null。
     */
    @SuppressWarnings("unchecked")
    private List<Object> batchZSetScoresPipelined(String zsetKey, List<String> members) {
        if (CollectionUtils.isEmpty(members)) {
            return Collections.emptyList();
        }
        return stringRedisTemplate.executePipelined(new SessionCallback<>() {
            @Override
            public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                ZSetOperations<K, V> zSetOps = operations.opsForZSet();
                for (String member : members) {
                    zSetOps.score((K) zsetKey, (V) member);
                }
                return null;
            }
        });
    }

    private static boolean isZSetScorePresent(Object score) {
        if (score == null) {
            return false;
        }
        if (score instanceof Boolean boolScore) {
            return boolScore;
        }
        return true;
    }

    private static int countPresentZSetScores(List<Object> scores) {
        int count = 0;
        for (Object score : scores) {
            if (isZSetScorePresent(score)) {
                count++;
            }
        }
        return count;
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
        String offsetKey = CacheConstant.buildSessionReadMessageOffsetCacheKey(metadata.getAppKey(), identityType.value(), from, packet.getDeviceType(), to);
        final long incomingOffset = maxReadPacketId;
        final String appKey = metadata.getAppKey();
        return Mono.fromCallable(() -> {
                    // ARGV 必须传 Long 等数值类型：String 经 Jackson 会序列化为 "123"（带引号），Lua tonumber 失败导致 SET 不执行
                    DefaultRedisScript<Long> readOffsetScript = new DefaultRedisScript<>(
                            LuaScriptEnum.READ_OFFSET_MAX_SCRIPT.getScript(), Long.class);
                    redisTemplate.execute(readOffsetScript, List.of(offsetKey), incomingOffset, expireTime);
                    if (identityType == IdentityType.ONE_2_ONE) {
                        refreshSessionPeerUnreadAfterRead(appKey, from, to, expireTime);
                    }
                    return Boolean.TRUE;
                })
                .doOnError(e -> log.error("已读回执 Redis 更新失败 | offsetKey={}, incomingOffset={}, expireTime={}",
                        offsetKey, incomingOffset, expireTime, e))
                .subscribeOn(Schedulers.boundedElastic());
    }

    /**
     * 单聊已读后按多端合并游标重算他人未读计数（支持部分已读，不再一律清零）。
     */
    private void refreshSessionPeerUnreadAfterRead(String appKey, String viewerId, String peerUserId, long expireMs) {
        String sessionId = IdentityUtil.sessionId(viewerId, peerUserId);
        long readOffset = resolveMaxReadOffsetAllDevices(appKey, IdentityType.ONE_2_ONE, viewerId, peerUserId);
        Object lastObj = redisTemplate.opsForValue().get(CacheConstant.buildSessionLastMessageCacheKey(appKey, sessionId));
        long lastMsgId = lastObj instanceof Number n ? n.longValue() : 0L;
        String peerUnreadKey = CacheConstant.buildSessionPeerUnreadCacheKey(appKey, viewerId, sessionId);
        if (lastMsgId <= readOffset) {
            redisTemplate.opsForValue().set(peerUnreadKey, 0L, expireMs, TimeUnit.MILLISECONDS);
            return;
        }
        int peerUnread = countPeerUnreadInSessionRange(appKey,
                CacheConstant.buildSessionCacheKey(appKey, sessionId), viewerId, readOffset, lastMsgId);
        redisTemplate.opsForValue().set(peerUnreadKey, (long) peerUnread, expireMs, TimeUnit.MILLISECONDS);
    }

    private int countPeerUnreadInSessionRange(String appKey, String sessionRedisKey, String viewerUserId,
                                              long readOffsetExclusive, long lastMsgIdInclusive) {
        int maxScan = MessageConstant.SESSION_UNREAD_PEER_SCAN_LIMIT;
        Range<String> range = Range.of(
                Range.Bound.exclusive(MessageContext.idGenerator().formatLongId19Str(readOffsetExclusive)),
                Range.Bound.inclusive(MessageContext.idGenerator().formatLongId19Str(lastMsgIdInclusive)));
        Set<String> members = stringRedisTemplate.opsForZSet().reverseRangeByLex(sessionRedisKey, range, Limit.limit().count(maxScan));
        if (CollectionUtils.isEmpty(members)) {
            return 0;
        }
        List<Long> packetIds = members.stream()
                .map(m -> {
                    try {
                        return Long.parseLong(m.trim());
                    } catch (NumberFormatException e) {
                        return 0L;
                    }
                })
                .filter(id -> id > 0)
                .toList();
        if (packetIds.isEmpty()) {
            return 0;
        }
        List<Packet> packets = getPackets(appKey, packetIds);
        int peerCount = 0;
        for (Packet packet : packets) {
            if (packet == null || packet.getMessage() == null) {
                continue;
            }
            Message message = packet.getMessage();
            if (viewerUserId.equals(message.getFrom())) {
                continue;
            }
            if (!isCountablePeerUnreadContentType(message.getContentType())) {
                continue;
            }
            peerCount++;
        }
        return peerCount;
    }

    private static boolean isCountablePeerUnreadContentType(int contentType) {
        return contentType != MessageContentTypeEnum.READ_RECEIPT_CONTENT.getType()
                && contentType != MessageContentTypeEnum.WITHDRAW_CONTENT.getType()
                && contentType != MessageContentTypeEnum.PING_PONG_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_REQUEST_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_RESPONSE_FAIL_CONTENT.getType()
                && contentType != MessageContentTypeEnum.LOGIN_RESPONSE_SUCCESS_CONTENT.getType()
                && contentType != MessageContentTypeEnum.QOS_DUP_CONTENT.getType()
                && contentType != MessageContentTypeEnum.GROUP_REQUEST_CONTENT.getType();
    }

    private static boolean isOneToOneSession(String sessionId, String from, String to) {
        return StringUtils.isNoneBlank(sessionId, from, to) && sessionId.equals(IdentityUtil.sessionId(from, to));
    }

    private void decrSessionPeerUnread(String appKey, String viewerId, String sessionId, int count, long expireMs) {
        if (count <= 0) {
            return;
        }
        String key = CacheConstant.buildSessionPeerUnreadCacheKey(appKey, viewerId, sessionId);
        Long remaining = redisTemplate.opsForValue().decrement(key, count);
        if (remaining == null) {
            return;
        }
        if (remaining < 0) {
            redisTemplate.opsForValue().set(key, 0L, expireMs, TimeUnit.MILLISECONDS);
        } else if (expireMs > 0) {
            redisTemplate.expire(key, Duration.ofMillis(expireMs));
        }
    }

    private static long resolveReadOffsetValue(Object existing) {
        if (existing instanceof Number number) {
            return number.longValue();
        }
        if (existing instanceof String str && StringUtils.isNotBlank(str)) {
            return Long.parseLong(str);
        }
        return 0L;
    }

    /**
     * 单聊：接收方他人消息未读 +1（群聊 sessionKey 为群 id，跳过）。
     */
    private void maybeIncrSessionPeerUnread(RedisConnection conn, Message message, String appKey,
                                            String sessionRedisKey, long expireMs) {
        if (message == null || StringUtils.isBlank(appKey) || StringUtils.isBlank(sessionRedisKey)) {
            return;
        }
        int contentType = message.getContentType();
        if (!isCountablePeerUnreadContentType(contentType)) {
            return;
        }
        String from = message.getFrom();
        String to = message.getTo();
        if (StringUtils.equals(from, to)) {
            return;
        }
        if (StringUtils.equals(sessionRedisKey, CacheConstant.buildSessionCacheKey(appKey, to))) {
            return;
        }
        String sessionId = IdentityUtil.sessionId(from, to);
        if (!StringUtils.equals(sessionRedisKey, CacheConstant.buildSessionCacheKey(appKey, sessionId))) {
            return;
        }
        byte[] peerUnreadKeyBytes = serializeOrNull(stringSerializer,
                CacheConstant.buildSessionPeerUnreadCacheKey(appKey, to, sessionId));
        if (peerUnreadKeyBytes == null) {
            return;
        }
        conn.commands().incr(peerUnreadKeyBytes);
        if (expireMs > 0) {
            conn.keyCommands().pExpire(peerUnreadKeyBytes, expireMs);
        }
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
            GroupUserEntity groupUserEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
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
                                                return jdbcClient.sql(JdbcSqlDialectHolder.selectGroupUser())
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
            redisTemplate.opsForValue().set(cacheKey, groupUserEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
        // 修正：score 存储的是角色值（GroupUserPost），需用 rangeByScore 而非 range（后者按 rank 索引）
        return stringRedisTemplate.opsForZSet().rangeByScore(
                CacheConstant.buildGroupUserCacheKey(appKey, message.getTo()),
                GroupUserPost.MANAGER.value(), GroupUserPost.MANAGER.value());
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
        Set<String> leanderIdentitySet = stringRedisTemplate.opsForZSet().rangeByScore(
                CacheConstant.buildGroupUserCacheKey(appKey, message.getTo()),
                GroupUserPost.LEADER.value(), GroupUserPost.LEADER.value());
        if (CollectionUtils.isEmpty(leanderIdentitySet)) {
            log.warn("群 {} 中不存在群主", message.getTo());
            return null;
        }
        return leanderIdentitySet.iterator().next();
    }



    /**
     * 保存业务消息热 key 与会话 ZSet 索引
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public Mono<Boolean> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return Mono.fromCallable(() -> saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildSessionCacheKey(metadata.getAppKey(), sessionId), (ops) -> {}, (ops, msg, app, f, t) -> {}))
                // 指定在弹性线程池中执行阻塞操作，避免阻塞Netty事件循环
                .subscribeOn(Schedulers.boundedElastic())
                // 响应式错误处理
                .onErrorResume(e -> {
                    log.error("Reactive save message failed: {}", e.getMessage(), e);
                    return Mono.just(false);
                });
    }


    /**
     * 保存加好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean saveJoinFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisConnection)-> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getFrom(), message.getTo());
            // 修复：key 必须用 stringSerializer，与 saveRefuseFriendRequestMessage 保持一致，否则后续读取时无法命中
            byte[] keyBytes = serializeOrNull(stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
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
    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisConnection)-> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(message.getMetadata().getAppKey(), message.getTo(), message.getFrom());
            byte[] keyBytes = serializeOrNull(stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    /**
     * 保存好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    private<K,V> boolean saveFriendRequestMessage(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        // 调用公共方法，传入空的额外操作
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String from = message.getFrom();
        String to = message.getTo();
        return saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer, (ops, msg, ak, f, t) -> {});
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
            friendEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
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
                                                return jdbcClient.sql(JdbcSqlDialectHolder.selectFriend())
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
            redisTemplate.opsForValue().set(cacheKey, friendEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
            redisTemplate.opsForValue().set(cacheKey, groupEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
            GroupEntity groupEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectGroup())
                    .param(GroupEntity.Fields.id, groupId)
                    .query(GroupEntity.class)
                    .single();
            // 走不到这里就会进异常
            redisTemplate.opsForValue().set(CacheConstant.buildGroupCacheKey(appKey, groupId), groupEntity,
                    MessageConstant.CACHE_ENTITY_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
            userEntity = jdbcClient.sql(JdbcSqlDialectHolder.selectUser())
                    .param(UserEntity.Fields.id, identity)
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
                                                return jdbcClient.sql(JdbcSqlDialectHolder.selectUser())
                                                        .param(UserEntity.Fields.id, identity)
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
    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection)-> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getFrom(), message.getTo());
            byte[] keyBytes = serializeOrNull(stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    /**
     * 同意绑定好友关系，在缓存中
     * @param appKey
     * @param packet
     * @return
     */
    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisConnection)-> {
            String friendRequestCacheKey = CacheConstant.buildFriendRequestCacheKey(appKey, message.getTo(), message.getFrom());
            byte[] keyBytes = serializeOrNull(stringSerializer, friendRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, requestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }


    /**
     * 绑定好友关系，在缓存中
     * @param packet
     * @param expireTime
     * @param consumer
     * @return
     */
    private<K,V> boolean bindFriend(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String from = message.getFrom();
        String to = message.getTo();
        String appKey = metadata.getAppKey();
        return saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(appKey, packet.getPacketId()), CacheConstant.buildFriendRequestSessionCacheKey(appKey, IdentityUtil.sessionId(from, to), friendRequestSessionId), consumer,
                (redisConnection, msg, ak, f, t) -> {
                    // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
                    // 建立双向好友关系（仅bindFriend方法需要的逻辑）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    redisConnection.zSetCommands().zAdd(stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, from)), msg.getMetadata().getServerTime() , stringSerializer.serialize(t));
                    redisConnection.zSetCommands().zAdd(stringSerializer.serialize(CacheConstant.buildFriendsCacheKey(appKey, to)), msg.getMetadata().getServerTime(), stringSerializer.serialize(f));
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
    private<K, V> boolean saveMessageWithSession(Packet packet, long expireTime, String messageKey, String sessionKey, Consumer<RedisConnection> consumer, FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        return isSaveAccepted(saveMessageWithSessionOutcome(packet, expireTime, messageKey, sessionKey, consumer, extraOperation));
    }

    @SuppressWarnings("unchecked")
    private<K, V> SaveMessageOutcome saveMessageWithSessionOutcome(Packet packet, long expireTime, String messageKey, String sessionKey, Consumer<RedisConnection> consumer, FiveConsumer<RedisConnection, Message, String, String, String> extraOperation) {
        if (packet == null || redisTemplate.getConnectionFactory() == null) {
            log.error("Packet 或 RedisConnectionFactory 为空");
            return SaveMessageOutcome.FAILED;
        }


        RedisConnectionFactory connectionFactory = redisTemplate.getConnectionFactory();

        try (RedisConnection conn = connectionFactory.getConnection()) {
            log.debug("获取 Redis 连接成功: {}", conn.hashCode());

            conn.openPipeline();
            log.debug("Pipeline 已开启");

            // === 解析业务参数 ===
            Message message = packet.getMessage();
            Metadata metadata = message != null ? message.getMetadata() : null;
            if (message == null || metadata == null) {
                log.error("消息或元数据为空");
                return SaveMessageOutcome.FAILED;
            }

            String appKey = metadata.getAppKey();
            String from = message.getFrom();
            String to = message.getTo();
            boolean qosSave = MessageContext.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel();
            if (qosSave && !QosIdempotencyHelper.tryClaim(redisTemplate, appKey, packet.getPacketId(), from, message.getId())) {
                return SaveMessageOutcome.DUPLICATE;
            }
            String formatPacketId = MessageContext.idGenerator().formatLongId19Str(packet.getPacketId());

            byte[] packetIdBytes = serializeOrThrow(stringSerializer, formatPacketId, "PacketId");
            byte[] msgKeyBytes = serializeOrNull(stringSerializer, messageKey);
            byte[] packetBytes = serializeOrNull(valueSerializer, packet);

            // === 步骤6：保存消息主体 SET + PEXPIRE ===
            if (msgKeyBytes != null && packetBytes != null) {
                conn.commands().set(msgKeyBytes, packetBytes);
                if (expireTime > 0) {
                    conn.keyCommands().pExpire(msgKeyBytes, expireTime);
                }
                log.debug("消息主体命令入队: {}", messageKey);
            } else {
                log.warn("消息主体序列化失败，跳过");
            }

            // === 步骤7：会话 ZSet 添加 ZADD ===
            byte[] sessionKeyBytes = serializeOrNull(stringSerializer, sessionKey);
            if (sessionKeyBytes != null) {
                conn.zAdd(sessionKeyBytes, NumberConstant.NUMBER_0, packetIdBytes);
                long sessionZSetExpireMs = MessageConstant.CACHE_SESSION_LAST_MESSAGE_KEY_EXPIRE_TIMESTAMP;
                if (sessionZSetExpireMs > 0) {
                    conn.keyCommands().pExpire(sessionKeyBytes, sessionZSetExpireMs);
                }
                // 裁剪超过上限的最老条目，防止 ZSet 无界增长（保留最新 SESSION_ZSET_MAX_SIZE 条）
                conn.zSetCommands().zRemRange(sessionKeyBytes, 0, -(MessageConstant.SESSION_ZSET_MAX_SIZE + 1L));
                log.debug("会话ZSet命令入队: {}", sessionKey);
                maybeIncrSessionPeerUnread(conn, message, appKey, sessionKey, sessionZSetExpireMs);
            }

            // === 步骤8：额外差异化操作 ===
            if (extraOperation != null) {
                safelyExecute(() -> extraOperation.accept(conn, message, appKey, from, to), "额外操作");
            }

            // === 步骤10：自定义逻辑 ===
            if (consumer != null) {
                safelyExecute(() -> consumer.accept(conn), "自定义逻辑");
            }

            // === 步骤11：关闭 Pipeline 并获取结果 ===
            List<Object> results;
            try {
                results = conn.closePipeline();
                log.debug("Pipeline 关闭成功，结果数量: {}", results.size());
            } catch (Exception e) {
                log.error("Pipeline 执行失败: ", e);
                forceClosePipeline(conn);
                if (qosSave) {
                    QosIdempotencyHelper.releaseClaim(redisTemplate, appKey, packet.getPacketId(), from, message.getId());
                }
                return SaveMessageOutcome.FAILED;
            }

            if (CollectionUtils.isEmpty(results)) {
                if (qosSave) {
                    QosIdempotencyHelper.releaseClaim(redisTemplate, appKey, packet.getPacketId(), from, message.getId());
                }
                return SaveMessageOutcome.FAILED;
            }
            // Pipeline 内的 SET 已通过 closePipeline() 的结果保证执行，无需额外 EXISTS 验证（消除多余 RTT）
            return SaveMessageOutcome.SUCCESS;

        } catch (Exception e) {
            log.error("Redis Pipeline 操作异常: ", e);
            // 兜底释放 QoS 占位（外层异常通常发生在获取连接/参数解析阶段，claim 可能尚未写入；
            // 但若已 tryClaim 成功后才抛错，则必须释放避免后续重试被判定为重复而永久丢失）
            try {
                Message message = packet != null ? packet.getMessage() : null;
                Metadata metadata = message != null ? message.getMetadata() : null;
                if (metadata != null && MessageContext.isQosEnable()
                        && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
                    QosIdempotencyHelper.releaseClaim(redisTemplate, metadata.getAppKey(),
                            packet.getPacketId(), message.getFrom(), message.getId());
                }
            } catch (Exception ignored) {
                // 释放失败不影响主流程，仅记录日志
                log.warn("释放 QoS 占位异常", ignored);
            }
            return SaveMessageOutcome.FAILED;
        }
    }

    private static boolean isSaveAccepted(SaveMessageOutcome outcome) {
        return outcome == SaveMessageOutcome.SUCCESS || outcome == SaveMessageOutcome.DUPLICATE;
    }

    /**
     * 释放 QoS 幂等占位键。当持久化成功但下游（Kafka/MQ/业务逻辑）失败时，
     * 处理器应调用此方法释放 claim，否则客户端重试会被判定为重复消息导致永久丢失。
     *
     * @param packet 消息包
     */
    public void releaseQosClaim(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || !MessageContext.isQosEnable()
                || message.getQos() <= QosLevelEnum.QOS_0.getLevel()) {
            return;
        }
        try {
            QosIdempotencyHelper.releaseClaim(redisTemplate, metadata.getAppKey(),
                    packet.getPacketId(), message.getFrom(), message.getId());
        } catch (Exception e) {
            log.warn("释放 QoS 占位异常: packetId={}", packet.getPacketId(), e);
        }
    }

    private <T> byte[] serializeOrNull(RedisSerializer<T> serializer, T value) {
        try {
            return serializer.serialize(value);
        } catch (Exception e) {
            log.warn("序列化失败: {}", value, e);
            return null;
        }
    }

    private <T> byte[] serializeOrThrow(RedisSerializer<T> serializer, T value, String fieldName) {
        byte[] bytes = serializeOrNull(serializer, value);
        if (bytes == null) throw new IllegalArgumentException(fieldName + " 序列化失败: " + value);
        return bytes;
    }

    private boolean allNotNull(byte[]... arrays) {
        for (byte[] arr : arrays) {
            if (arr == null) return false;
        }
        return true;
    }

    private void safelyExecute(Runnable runnable, String opName) {
        try {
            runnable.run();
            log.debug("{} 执行完成", opName);
        } catch (Exception e) {
            log.error("{} 执行失败: {}", opName, e.getMessage(), e);
        }
    }

    private void forceClosePipeline(RedisConnection conn) {
        try {
            if (!conn.isClosed()) {
                conn.closePipeline();
            }
        } catch (Exception e) {
            log.error("强制关闭Pipeline失败", e);
        }
    }


    /**
     * 自动通过绑定群组关系
     * @return
     */
    public boolean autoPassBindGroup(Packet packet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection)-> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = serializeOrNull(stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }

    /**
     * 手动通过绑定群组关系
     * @return
     */
    public boolean manualPassBindGroup(Packet packet,  GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(),  expireTime, (redisConnection)-> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = serializeOrNull(stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }




    /**
     * 绑定好友关系，在缓存中
     * @param packet
     * @param expireTime
     * @param consumer
     * @return
     */
    @SuppressWarnings("unchecked")
    private<K,V> boolean bindGroup(Packet packet, String joiner, String groupId, String requestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), consumer, (redisConnection, msg, ak, f, t) -> {
                    // 1. 获取 String 序列化器（与前文保持一致，确保序列化规则统一）
                    // 建立双向好友关系（仅bindFriend方法需要的逻辑）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    redisConnection.zSetCommands().zAdd(stringSerializer.serialize(CacheConstant.buildGroupUserCacheKey(metadata.getAppKey(), groupId)), GroupUserPost.ORDINARY.value() , stringSerializer.serialize(joiner));
                    redisConnection.zSetCommands().zAdd(stringSerializer.serialize(CacheConstant.buildUserGroupsCacheKey(metadata.getAppKey(), joiner)), msg.getMetadata().getServerTime(), stringSerializer.serialize(groupId));
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
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection)-> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = serializeOrNull(stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.SET_IF_ABSENT);
        });
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
        return saveGroupRequestMessage(packet, groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisConnection)-> {
            String groupRequestCacheKey = CacheConstant.buildGroupRequestCacheKey(metadata.getAppKey(), groupRequestSession.getJoiner(), groupRequestSession.getGroupId());
            byte[] keyBytes = serializeOrNull(stringSerializer, groupRequestCacheKey);
            byte[] valueBytes = serializeOrNull(valueSerializer, groupRequestSession);
            redisConnection.commands().set(keyBytes, valueBytes, Expiration.milliseconds(MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP), RedisStringCommands.SetOption.UPSERT);
        });
    }


    /**
     * 保存群组请求消息
     * @param packet
     * @param expireTime
     * @return
     */
    public<K,V> boolean saveGroupRequestMessage(Packet packet, String groupId, String requestSessionId, long expireTime, Consumer<RedisConnection> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return saveMessageWithSession(packet, expireTime, CacheConstant.buildMessageCacheKey(metadata.getAppKey(), packet.getPacketId()), CacheConstant.buildGroupRequestSessionCacheKey(metadata.getAppKey(), groupId, requestSessionId), consumer, (ops, msg, ak, f, t) -> {});
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
