package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.*;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.db.jdbc.JdbcFactory;
import com.ouyunc.db.mongo.MongodbFactory;
import com.ouyunc.domain.entity.FriendEntity;
import com.ouyunc.domain.entity.MessageEntity;
import com.ouyunc.domain.entity.MongoMessageEntity;
import com.ouyunc.mq.kafka.KafkaFactory;
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
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
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
    private static final RedisTemplate<String, ?> redisTemplate = CacheFactory.REDIS.instance();

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
      return savePacket2Mq(MqConstant.KAFKA_SAVE_MESSAGE_TOPIC, packet);
    }


    /**
     * 将消息保存到mq中
     * @param topic
     * @param packet
     * @return
     */
    public CompletableFuture<?> savePacket2Mq(String topic, Packet packet) {
        // 保存消息到磁盘中，这里使用mq来提高吞吐量；如果kafka
        // headers 中可以自定义一些信息做扩展；
        Map<String, Object> headers = new HashMap<>();
        headers.put(KafkaHeaders.CORRELATION_ID, packet.getPacketId());
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
     * 撤销消息，
     * 注意：这里没有做判断，被撤销的消息是否属于发起撤销的客户端，一般情况下是需要做判断的
     */
    @SuppressWarnings("unchecked")
    public boolean withdrawMessage(Packet packet, String sessionId, Set<String> withdrawIdentitySet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 获取需要撤销的消息id，（这里使用String类型接收）
        List<Long> packetIds = JSON.parseArray(message.getContent(), Long.class);
        // 如果没有被撤销的消息id，则直接返回false
        if (CollectionUtils.isEmpty(packetIds) || packetIds.size() > MessageConstant.MAX_WITHDRAW_MESSAGE_COUNT) {
            log.error("撤销消息数量为0或超出限制!");
            return false;
        }
        // 获取需要撤销的消息的服务端时间戳，这个获取要在会话锁的前提下获取,注意批量获取score 的方法是redis 6.2.0 之后的版本才支持,如果不支持请使用其他方式替换，或升级redis版本，这里 就使用lua 脚本 哈哈哈

        // 获取消息在会话中的消息服务端时间戳
        List<Long> messageServerTimeScores = redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_SCORE_LUA_SCRIPT, List.class), List.of(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId), packetIds.toArray());
        if (CollectionUtils.isEmpty(messageServerTimeScores) || messageServerTimeScores.parallelStream().filter(Objects::nonNull).count() != packetIds.size()) {
            log.error("会话:{}不存在该消息id: {}, 或消息id 对应会话中的消息数量不相等", sessionId, packetIds);
            return false;
        }
        // 获取消息
        List<Packet> withdrawPackets = getPackets(metadata.getAppKey(), packetIds);
        if (CollectionUtils.isEmpty(withdrawPackets) || withdrawPackets.size() != packetIds.size()) {
            log.error("消息id: {} 对应的消息数量不相等！", packetIds);
            return false;
        }
        // 判断需要被撤回的消息是否属于该会话，且都属于发送者
        for (Packet withDrawPacket : withdrawPackets) {
            if (withDrawPacket == null || !withDrawPacket.getMessage().getFrom().equals(message.getFrom())) {
                log.error("被撤销的消息id: {} 对应的消息不属于发送者！", packetIds);
                return false;
            }
        }
        // 批量撤回消息
        List<String> keys = Lists.newArrayList();
        keys.add(String.valueOf(withdrawIdentitySet.size()));
        for (Long packetId : packetIds) {
            keys.add(buildMessageRedisKey(metadata.getAppKey(), packetId));
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId);
            for (String withdrawIdentity : withdrawIdentitySet) {
                keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + withdrawIdentity);
            }
        }
        return redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT, Boolean.class), keys, packetIds.toArray());
    }

    /**
     * 处理读已回执消息
     */
    public boolean readReceiptMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 已读的消息id，（这里使用String类型接收）
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        // 如果已读的消息id，则直接返回false
        if (CollectionUtils.isEmpty(readPacketIds) || readPacketIds.size() > MessageConstant.MAX_READ_RECEIPT_MESSAGE_COUNT) {
            log.error("已读回执消息数量为0或超出限制!");
            return false;
        }

        // 获取需要撤销的消息的服务端时间戳
        List<Long> messageServerTimeScores = redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_SCORE_LUA_SCRIPT, List.class), List.of(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId), readPacketIds.toArray());
        if (CollectionUtils.isEmpty(messageServerTimeScores) || messageServerTimeScores.parallelStream().filter(Objects::nonNull).count() != readPacketIds.size()) {
            log.error("会话:{}不存在该消息id: {}, 或消息id 对应会话中的消息数量不相等", sessionId, readPacketIds);
            return false;
        }
        // 获取消息
        List<Packet> readReceiptPackets = getPackets(metadata.getAppKey(), readPacketIds);
        if (CollectionUtils.isEmpty(readReceiptPackets) || readReceiptPackets.size() != readPacketIds.size()) {
            log.error("读已回执消息ids: {} 对应的消息数量不相等！", readPacketIds);
            return false;
        }
        // 判断已读的消息是否属于该会话，且不属于发送者
        for (Packet readReceiptPacket : readReceiptPackets) {
            if (readReceiptPacket == null || readReceiptPacket.getMessage().getFrom().equals(message.getFrom())) {
                log.error("已读回执的消息id: {} 对应的消息属于发送者！", readReceiptPacket);
                return false;
            }
        }
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
        return redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_READ_RECEIPT_MESSAGE_LUA_SCRIPT, Boolean.class), keys, args.toArray());
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
     * 保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean saveMessage(Packet packet, String sessionId, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String luaScript = LuaScriptConstant.SAVE_MESSAGE_WITHOUT_OFFLINE_LUA_SCRIPT;
        List<String> keys = Lists.newArrayList(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId(),
                CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId);
        Object[] args = new Object[]{packet, expireTime, packet.getPacketId(), metadata.getServerTime()};
        // 如果开启qos,并且需要qos
        if (MessageContext.messageProperties.isQosEnable() && message.getQos() > QosLevelEnum.QOS_0.getLevel()) {
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo());
            luaScript = LuaScriptConstant.SAVE_MESSAGE_WITH_OFFLINE_LUA_SCRIPT;
        }
        return redisTemplate.execute(new DefaultRedisScript<>(luaScript, Boolean.class), keys, args);
    }

    /**
     * 群组批量保存，保存业务消息以及离线消息和会话消息
     * @param packet
     * @param expireTime 过期时间，单位毫秒，多久后过期
     * @return
     */
    public boolean batchSaveMessage(Packet packet, Set<String> groupUserIdentitySet, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 构造参数
        List<String> offlineKeys = groupUserIdentitySet.stream().map(groupUserIdentity -> CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + groupUserIdentity).toList();
        List<String> keys = new ArrayList<>();
        keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + packet.getPacketId());
        keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + message.getTo());
        keys.addAll(offlineKeys);

        List<Object> args = new ArrayList<>();
        args.add(expireTime);
        args.add(packet); // 需要实现序列化方法
        args.add(metadata.getServerTime());
        args.add(packet.getPacketId());
        args.add(metadata.getAppKey());
        args.add(message.getTo());
        return redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_SAVE_MESSAGE_LUA_SCRIPT, Boolean.class), keys, args.toArray());
    }


    /**
     * 判断在appKey 下 from 和 to 是否是好友关系
     * @param appKey
     * @param from
     * @param to
     * @return
     */
    public boolean isFriend(String appKey, String from, String to) {
        return redisTemplate.opsForZSet().score(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + from, to) != null;
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
        FriendEntity friendEntity = redisTemplate.<String, FriendEntity>opsForHash().get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS_CONFIG + from, to);
        // 如果不为空则返回true，如果为空则从数据库中获取
        if (friendEntity != null) {
            return friendEntity;
        }
        try {
            friendEntity = jdbcClient.sql(JdbcSqlConstant.MYSQL.SELECT_FRIEND.sql())
                    .params(from, to)
                    .query(FriendEntity.class)
                    .single();
        } catch (EmptyResultDataAccessException e) {
            return null;
        }catch (Exception e) {
            log.error("从db查询好友关系异常: {}", e.getMessage());
            throw new RuntimeException(e);
        }
        // 如果不为空，添加到缓存中
        redisTemplate.opsForHash().put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS_CONFIG + from, to, friendEntity);
        return friendEntity;
    }


    /**
     * 绑定好友关系，在缓存中
     * @param appKey
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public boolean bindFriend(String appKey, Packet packet) {
        Message message = packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        try {
            redisTemplate.executePipelined(new SessionCallback<>() {
                @Override
                public <K, V> Object execute(RedisOperations<K, V> operations) throws DataAccessException {
                    operations.opsForZSet().add((K) (CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + from), (V) to, message.getMetadata().getServerTime());
                    operations.opsForZSet().add((K) (CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.FRIENDS + to), (V) from, message.getMetadata().getServerTime());
                    return null;
                }
            });
        }catch (Exception e) {
            log.error("绑定好友关系失败: {}", e.getMessage());
            return false;
        }
        return true;
    }
}
