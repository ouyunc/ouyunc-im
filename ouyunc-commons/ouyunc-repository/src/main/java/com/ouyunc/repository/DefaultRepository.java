package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.FiveConsumer;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.utils.IdentityUtil;
import com.ouyunc.base.utils.SnowflakeUtil;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.base.GroupRequestSession;
import com.ouyunc.domain.base.RequestSession;
import com.ouyunc.domain.constants.GroupUserPost;
import com.ouyunc.domain.entity.*;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.dao.IncorrectResultSizeDataAccessException;
import org.springframework.data.mongodb.core.MongoTemplate;
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

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * @author fzx
 * @description 默认持久化仓库实现,注意如果子类不进行覆盖，则使用默认的操作器来处理数据
 */
public enum DefaultRepository implements Repository{
    INSTANCE;

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
     * redisTemplate
     */
    private static final RedisTemplate redisTemplate = CacheFactory.REDIS.instance();

    /**
     * StringRedisTemplate
     */
    private static final StringRedisTemplate stringRedisTemplate = CacheFactory.STRING_REDIS.instance();

    /**
     * ReactiveStringRedisTemplate
     */
    private static final ReactiveStringRedisTemplate reactiveStringRedisTemplate = CacheFactory.REACTIVE_STRING_REDIS.instance();

    /**
     * reactiveRedisTemplate
     */
    private static final ReactiveRedisTemplate reactiveRedisTemplate = CacheFactory.REACTIVE_REDIS.instance();

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
    @Override
    public boolean checkDup(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        Double score = redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON  + CacheConstant.OFFLINE + message.getTo(), packet.getPacketId());
        // 如果分数不为 null，则表示值存在
        return !Objects.isNull(score);
    }


    /**
     * 批量获取消息
     * @param appKey
     * @param packetIds
     * @return
     */
    @SuppressWarnings("unchecked")
    public List<Packet> getPackets(String appKey, List<Long> packetIds) {
        if (CollectionUtils.isEmpty(packetIds)) {
            log.warn("packetIds 为空, appKey={}", appKey);
            return Collections.emptyList(); // 避免返回 null
        }

        // 1. 从 Redis 批量获取缓存
        Set<String> redisKeys = packetIds.stream()
                .map(id -> buildMessageRedisKey(appKey, id))
                .collect(Collectors.toSet());
        List<Packet> cachedPackets = (List<Packet>) redisTemplate.opsForValue().multiGet(redisKeys);

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

        // 3. 从 MongoDB 和 MySQL 查询缺失数据 (合并查询逻辑)
        List<Packet> dbPackets = queryFromDatabases(missingIds);

        // 4. 合并结果并异步更新缓存
        List<Packet> result = mergeResults(cachedPacketMap, dbPackets);
        asyncUpdateCache(appKey, dbPackets);

        return result;
    }

//----------------------------- 辅助方法 -----------------------------

    /**
     * 构建 Redis Key
     */
    private String buildMessageRedisKey(String appKey, Long packetId) {
        return CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MESSAGE + packetId;
    }

    /**
     * 从 MongoDB 和 MySQL 查询数据 (优先级: MongoDB -> MySQL)
     */
    private List<Packet> queryFromDatabases(List<Long> missingIds) {
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
            List<MessageEntity> mysqlEntities = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_MESSAGE.sql())
                    .param(MessageEntity.Fields.ids, remainingIds)
                    .query(MessageEntity.class)
                    .list();
            dbPackets.addAll(convertToPackets(mysqlEntities));
        }

        return dbPackets;
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
     * 异步更新缓存 (非阻塞主流程)
     */
    @SuppressWarnings("unchecked")
    private void asyncUpdateCache(String appKey, List<Packet> dbPackets) {
        if (CollectionUtils.isEmpty(dbPackets)) {
            return;
        }
        CompletableFuture.runAsync(() -> {

            redisTemplate.executePipelined(new SessionCallback<Object>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    dbPackets.forEach(packet -> {
                        operations.opsForValue().set((K) buildMessageRedisKey(appKey, packet.getPacketId()), (V) packet,  MessageConstant.CACHE_MESSAGE_HOT_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
                    });
                    return null;
                }
            });
        }).exceptionally(ex -> {
            log.error("异步更新缓存失败", ex);
            return null;
        });
    }



    /**
     * 撤回消息校验
     * @param packet
     * @param sessionId
     * @return
     */
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
        });
    }


    /**
     * 响应式撤回消息校验
     * @param packet
     * @param sessionId
     * @return
     */
    public Mono<Boolean> reactiveValidReadReceiptMessage(Packet packet, String sessionId, boolean isValidSender) {
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
        });
    }



    /**
     * 验证特殊消息，校验通过返回true, 不通过返回false
     * @param packet
     * @param sessionId
     * @return
     */
    @SuppressWarnings("unchecked")
    private Mono<Boolean> reactiveValidSpecialMessage(Packet packet, String sessionId, Function<List<Packet>, Mono<Boolean>> function) {
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
        if (CollectionUtils.isEmpty(packetIds) || packetIds.size() > MessageConstant.MAX_WITHDRAW_MESSAGE_COUNT) {
            log.error("消息数量为0或超出限制 {}!", MessageConstant.MAX_WITHDRAW_MESSAGE_COUNT);
            return Mono.just(false);
        }
        // 获取需要消息服务端时间戳，这个获取要在会话锁的前提下获取,注意批量获取score 的方法是redis 6.2.0 之后的版本才支持,如果不支持请使用其他方式替换，或升级redis版本，这里 就使用lua 脚本 哈哈哈
        // 获取消息在会话中的消息服务端时间戳
        Flux<Long> scoreFlux = reactiveRedisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_SCORE_LUA_SCRIPT, List.class), List.of(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId), packetIds)
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
                                return function.apply(packets);
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
     * 响应式 撤销消息，
     * 注意：这里没有做判断，被撤销的消息是否属于发起撤销的客户端，一般情况下是需要做判断的
     */
    public Mono<Boolean> reactiveWithdrawMessage(Packet packet, String sessionId, Set<String> withdrawIdentitySet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 获取需要撤销的消息id，（这里使用String类型接收）
        List<Long> packetIds = JSON.parseArray(message.getContent(), Long.class);
        // 如果没有被撤销的消息id，则直接返回false
        // 批量撤回消息
        List<String> keys = Lists.newArrayList();
        keys.add(String.valueOf(withdrawIdentitySet.size()));
        List<String> formatPacketIdArgs = Lists.newArrayList();
        for (Long packetId : packetIds) {
            formatPacketIdArgs.add(SnowflakeUtil.formatLong(packetId));
            keys.add(buildMessageRedisKey(metadata.getAppKey(), packetId));
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId);
            for (String withdrawIdentity : withdrawIdentitySet) {
                keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + withdrawIdentity);
            }
        }
        Flux<Boolean> executeResult = reactiveStringRedisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT, Boolean.class), keys, formatPacketIdArgs);
        return executeResult.all(result -> result);
    }




    /**
     * 响应式处理读已回执消息  todo 这里换成位图 bitmap 实现，减少存储量, 这个已读后续要拿掉，这样针对大数据效率太低，直接使用偏移量来判断已读未读
     */
    public Mono<Boolean> reactiveReadReceiptMessage(Packet packet, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 已读的消息id，（这里使用String类型接收）
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        // 批量已读回执消息
        List<String> keys = Lists.newArrayList();
        List<Object> args = Lists.newArrayList();
        for (Long readPacketId : readPacketIds) {
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + readPacketId);
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.READ_MESSAGE + readPacketId);
            // 阅读人
            args.add(message.getFrom());
            // 已读时间
            args.add(metadata.getServerTime());
            // 整体过期时间，这里是毫秒
            args.add(expireTime);
        }
        Flux<Boolean> executeResult = reactiveStringRedisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_READ_RECEIPT_MESSAGE_LUA_SCRIPT, Boolean.class), keys, args);
        return executeResult.all(result -> result);
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
        return (Set<String>) redisTemplate.opsForZSet().range(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
    }


    /**
     * 获取群成员列表信息
     *
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<GroupUserEntity> groupUserEntitySet(String appKey, String groupId, Set<String> memberIdSet) {
        if (CollectionUtils.isEmpty(memberIdSet)) {
            return Set.of();
        }
        // 构造groupUserEntity 缓存key 集合
        Set<String> cacheGroupUserEntityKeyList = memberIdSet.stream().filter(Objects::nonNull).map(memberId -> CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS_CONFIG + memberId + CacheConstant.COLON + groupId).collect(Collectors.toSet());
        List<GroupUserEntity> groupUserEntityList = (List<GroupUserEntity>) redisTemplate.opsForValue().multiGet(cacheGroupUserEntityKeyList);
        if (CollectionUtils.isEmpty(groupUserEntityList)) {
            return Set.of();
        }
        // 获取群组用户信息
        return groupUserEntityList.stream().filter(Objects::nonNull).collect(Collectors.toSet());
    }



    /**
     * 获取群成员信息
     * @return
     */
    @SuppressWarnings("unchecked")
    public GroupUserEntity groupUserEntity(String appKey, String groupId, String memberId) {
        return (GroupUserEntity) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS_CONFIG + memberId + CacheConstant.COLON + groupId);
    }


    /**
     * 获取群管理员和群主的唯一标识
     *
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupManagerAndLeaderUsersIdentity(Packet packet) {
        return redisTemplate.opsForZSet().rangeByScore(CacheConstant.OUYUNC + CacheConstant.APP_KEY + packet.getMessage().getMetadata().getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_USERS + packet.getMessage().getTo(), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
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
        Set<String> groupManagerAndLeaderUsersIdentitySet = redisTemplate.opsForZSet().rangeByScore(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), GroupUserPost.MANAGER.value(), GroupUserPost.LEADER.value());
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
        return redisTemplate.opsForZSet().range(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), GroupUserPost.MANAGER.value(), GroupUserPost.MANAGER.value());
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
        Set<String> leanderIdentitySet = redisTemplate.opsForZSet().range(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), GroupUserPost.LEADER.value(), GroupUserPost.LEADER.value());
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
    public Mono<Boolean> reactiveSaveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String requestSessionKey = CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId;
        String offlineKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo();
        // 使用Mono.fromCallable将阻塞操作包装为响应式流
        return Mono.fromCallable(() -> saveMessageWithSessionOrOffline(packet, expireTime, messageKey, requestSessionKey, List.of(offlineKey), (ops) -> {}, (ops, msg, app, f, t) -> {}))
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
    public Mono<Boolean> reactiveSaveOfflineMessage(Packet packet, String to) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return reactiveRedisTemplate.opsForZSet().add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + to, packet.getPacketId(), metadata.getServerTime());
    }


    /**
     * 保存加好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean saveJoinFriendRequestMessage(Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        // 获取原来存在的sessionid
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisOperations)-> {
            redisOperations.opsForValue().setIfAbsent(CacheConstant.OUYUNC + CacheConstant.APP_KEY + message.getMetadata().getAppKey() + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_SESSION + message.getFrom() + CacheConstant.COLON + message.getTo(), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS);
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
        return  (RequestSession) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_SESSION + from + CacheConstant.COLON + to);
    }

    /**
     * 获取好友请求会话信息
     * @param appKey
     * @param joiner
     * @param groupId
     * @return
     */
    public GroupRequestSession getGroupRequestSession(String appKey, String joiner, String groupId) {
        return  (GroupRequestSession) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + joiner + CacheConstant.COLON + groupId);
    }

    /**
     * 保存拒绝好友请求,
     * @param packet
     * @param expireTime
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean saveRefuseFriendRequestMessage(Packet packet, RequestSession requestSession,  long expireTime) {
        Message message = packet.getMessage();
        return saveFriendRequestMessage(packet, requestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + message.getMetadata().getAppKey() + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_SESSION + message.getTo() + CacheConstant.COLON + message.getFrom(), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 保存好友请求,
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    private<K,V> boolean saveFriendRequestMessage(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisOperations<K, V>> consumer) {
        // 调用公共方法，传入空的额外操作
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata.getAppKey();
        String from = message.getFrom();
        String to = message.getTo();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String requestSessionKey = CacheConstant.OUYUNC +  CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST + CacheConstant.SESSION + IdentityUtil.sessionId(from, to) + CacheConstant.COLON + friendRequestSessionId;
        String offlineKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo();
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, requestSessionKey, List.of(offlineKey), consumer, (ops, msg, ak, f, t) -> {});
    }




    /**
     * 群组批量保存，保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public Mono<Boolean> reactiveBatchSaveMessage(Packet packet, Set<String> groupUserIdentitySet, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String requestSessionKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + message.getTo();
        // 构造参数
        List<String> offlineKeys = groupUserIdentitySet.stream().map(groupUserIdentity -> CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + groupUserIdentity).toList();
        return Mono.fromCallable(() -> saveMessageWithSessionOrOffline(packet, expireTime, messageKey, requestSessionKey, offlineKeys, (ops) -> {}, (ops, msg, app, f, t) -> {}))
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
    public boolean isFriend(String appKey, String from, String to) {
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + from, to) != null;
    }

    /**
     * 判断在appKey 下 from 是否已经在群中
     * @param appKey
     * @param from
     * @param groupId
     * @return
     */
    public boolean inGroup(String appKey, String from, String groupId) {
        // 这里是否再去查询数据库？没有太大必要，后续如果需要再加
        return redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP_USERS + groupId, from) != null;
    }



    /**
     * 获取在appKey 下 from 和 to 的朋友关系
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    public FriendEntity getFriend(String appKey, String from, String to) {
        // 从redis中获取好友关系
        // 如果不为空则返回true，如果为空则不再从数据库中获取
        return (FriendEntity) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS_CONFIG + from + CacheConstant.COLON + to);
    }

    /**
     * 获取在appKey 下 from 和 to 的朋友关系
     * @param appKey
     * @param groupId
     * @return
     */
    public GroupEntity getGroupEntity(String appKey, String groupId) {
        // 从redis中获取群信息
        // 如果不为空则返回true，如果为空则不再从数据库中获取
        return (GroupEntity) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.GROUP + groupId);
    }


    /**
     * 获取用户实体, 注意这里直接从缓存获取，不在走数据库，旨在提高性能，要保证缓存中存在该用户实体， 后来想想还是加上吧，哈哈
     * @param identity
     * @return
     */
    public UserEntity getUserEntity(String appKey, String identity) {
        UserEntity userEntity = (UserEntity) redisTemplate.opsForValue().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.USER + identity);
        if (userEntity != null) {
            return userEntity;
        }
        try {
            log.info("从数据库中获取用户实体, identity: {}", identity);
            userEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_USER.sql())
                    .params(identity)
                    .query(UserEntity.class)
                    .single();
            // 存到缓存中,30天
            redisTemplate.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.USER + identity, userEntity, NumberConstant.NUMBER_30 * MessageConstant.DAY_TIMESTAMP, TimeUnit.MILLISECONDS);
            return userEntity;
        } catch (EmptyResultDataAccessException e) {
            log.error("用户不存在, identity: {}", identity);
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
     * 自动通过绑定好友关系，在缓存中
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean autoPassBindFriend(Packet packet, RequestSession requestSession,  long expireTime) {
        Message message = packet.getMessage();
        String appKey = message.getMetadata().getAppKey();
        // 获取是否存在sessionId
        // 注意过期时间的设定，与消息 hot key 的过期时间保持一致
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_SESSION + message.getFrom() + CacheConstant.COLON + message.getTo(), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 同意绑定好友关系，在缓存中
     * @param appKey
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean agreeBindFriend(String appKey, Packet packet, RequestSession requestSession, long expireTime) {
        Message message = packet.getMessage();
        // 注意过期时间的设定，与消息 hot key 的过期时间保持一致
        return bindFriend(packet, requestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST_SESSION + message.getTo() + CacheConstant.COLON + message.getFrom(), requestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }


    /**
     * 绑定好友关系，在缓存中
     * @param packet
     * @param expireTime
     * @param consumer
     * @return
     */
    @SuppressWarnings("unchecked")
    private<K,V> boolean bindFriend(Packet packet, String friendRequestSessionId, long expireTime, Consumer<RedisOperations<K, V>> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String from = message.getFrom();
        String to = message.getTo();
        String appKey = metadata.getAppKey();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String requestSessionKey = CacheConstant.OUYUNC +  CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIEND_REQUEST + CacheConstant.SESSION + IdentityUtil.sessionId(from, to) + CacheConstant.COLON + friendRequestSessionId;
        String offlineKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo();
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, requestSessionKey, List.of(offlineKey), consumer,
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
                    String fromFriendKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + from;
                    String toFriendKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + to;
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    conn.zAdd(stringSerializer.serialize(fromFriendKey), msg.getMetadata().getServerTime() , stringSerializer.serialize(t));
                    conn.zAdd(stringSerializer.serialize(toFriendKey), msg.getMetadata().getServerTime(), stringSerializer.serialize(f));
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
    private<K, V> boolean saveMessageWithSessionOrOffline(Packet packet, long expireTime, String messageKey, String sessionKey, List<String> offlineKeys, Consumer<RedisOperations<K, V>> consumer, FiveConsumer<RedisOperations<K, V>, Message, String, String, String> extraOperation) {
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
                    if (MessageContext.messageProperties.isQosEnable()  && message.getQos() > QosLevelEnum.QOS_0.getLevel() && CollectionUtils.isNotEmpty(offlineKeys)) {
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
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }

    /**
     * 手动通过绑定群组关系
     * @return
     */
    public boolean manualPassBindGroup(Packet packet,  GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        return bindGroup(packet, groupRequestSession.getJoiner(), groupRequestSession.getGroupId(), groupRequestSession.getSessionId(),  expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
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
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String sessionKey = CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + CacheConstant.SESSION + joiner + CacheConstant.COLON + requestSessionId;
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, sessionKey, null, consumer, (ops, msg, ak, f, t) -> {
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
                    String groupMemberKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_USERS + groupId;
                    String memberGroupKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUPS + joiner;
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 2. 使用字符串序列化器处理ZSet操作（保持原生字符串特性）
                    // 转换键和值为字符串类型的键
                    conn.zAdd(stringSerializer.serialize(groupMemberKey), GroupUserPost.ORDINARY.value() , stringSerializer.serialize(joiner));
                    conn.zAdd(stringSerializer.serialize(memberGroupKey), msg.getMetadata().getServerTime(), stringSerializer.serialize(groupId));
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
        return saveGroupRequestMessage(packet, groupRequestSession.getJoiner(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().setIfAbsent(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
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
        return saveGroupRequestMessage(packet, groupRequestSession.getJoiner(), groupRequestSession.getSessionId(), expireTime, (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS));
    }


    /**
     * 保存群组请求消息
     * @param packet
     * @param expireTime
     * @return
     */
    public<K,V> boolean saveGroupRequestMessage(Packet packet, String joiner, String requestSessionId, long expireTime, Consumer<RedisOperations<K,V>> consumer) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String sessionKey = CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + CacheConstant.SESSION + joiner + CacheConstant.COLON + requestSessionId;
        String offlineKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo();
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, sessionKey, List.of(offlineKey), consumer, (ops, msg, ak, f, t) -> {});
    }


    /**
     * 群组请求消息批量保存
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean batchSaveJoinGroupRequestMessage(Packet packet, Set<String> groupUserIdentitySet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String sessionKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + CacheConstant.SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getSessionId();
        List<String> offlineKeys = groupUserIdentitySet.stream().map(groupUserIdentity -> CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + groupUserIdentity).toList();
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, sessionKey, offlineKeys, (redisOperations)-> redisOperations.opsForValue().setIfAbsent(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS), (ops, msg, ak, f, t) -> {});

    }

    /**
     * 群组请求拒绝消息批量保存
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean batchSaveGroupRequestMessage(Packet packet, Set<String> groupUserIdentitySet, GroupRequestSession groupRequestSession, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String messageKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId();
        String sessionKey = CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST + CacheConstant.SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getSessionId();
        List<String> offlineKeys = groupUserIdentitySet.stream().map(groupUserIdentity -> CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + groupUserIdentity).toList();
        return saveMessageWithSessionOrOffline(packet, expireTime, messageKey, sessionKey, offlineKeys,  (redisOperations)-> redisOperations.opsForValue().set(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_REQUEST_SESSION + groupRequestSession.getJoiner() + CacheConstant.COLON + groupRequestSession.getGroupId(), groupRequestSession, MessageConstant.CACHE_REQUEST_SESSION_KEY_EXPIRE_TIMESTAMP, TimeUnit.MILLISECONDS), (ops, msg, ak, f, t) -> {});
    }
}
