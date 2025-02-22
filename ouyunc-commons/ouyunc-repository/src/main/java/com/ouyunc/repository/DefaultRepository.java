package com.ouyunc.repository;

import com.alibaba.fastjson2.JSON;
import com.google.common.collect.Lists;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.LuaScriptConstant;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.NumberConstant;
import com.ouyunc.base.constant.enums.QosLevelEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.support.MessageBuilder;

import java.util.*;
import java.util.concurrent.CompletableFuture;

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
        // 保存消息到磁盘中，这里使用mq来提高吞吐量；如果kafka
        // headers 中可以自定义一些信息做扩展；
        Map<String, Object> headers = new HashMap<>();
        headers.put(MessageHeaders.ID, packet.getPacketId());
        headers.put(KafkaHeaders.TOPIC, MqConstant.KAFKA_SAVE_MESSAGE_TOPIC);
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
     * 撤销消息
     */
    public boolean withdrawMessage(Packet packet, String sessionId) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 获取需要撤销的消息id，（这里使用String类型接收）
        List<Long> packetIds = JSON.parseArray(message.getContent(), Long.class);
        // 如果没有被撤销的消息id，则直接返回false
        if (CollectionUtils.isEmpty(packetIds)) {
            return false;
        }
        // 批量撤回消息
        List<String> keys = Lists.newArrayList();
        Object[] args = new Object[packetIds.size()];
        for (int i = 0; i < packetIds.size(); i++) {
            Long withdrawPacketId = packetIds.get(i);
            keys.add(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.MESSAGE + withdrawPacketId);
            keys.add(CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.SESSION + sessionId);
            keys.add(CacheConstant.OUYUNC +  CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.OFFLINE + message.getTo());
            args[i] = withdrawPacketId;
        }
        return redisTemplate.execute(new DefaultRedisScript<>(LuaScriptConstant.BATCH_WITHDRAW_MESSAGE_LUA_SCRIPT, Boolean.class), keys, args);
    }

    /**
     * 处理读已回执消息
     */
    public boolean readReceiptMessage(Packet packet, long expireTime) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // 已读的消息id，（这里使用String类型接收）
        List<Long> readPacketIds = JSON.parseArray(message.getContent(), Long.class);
        // 如果已读的消息id，则直接返回false
        if (CollectionUtils.isEmpty(readPacketIds)) {
            return false;
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
     * @param packet
     * @return
     */
    @SuppressWarnings("unchecked")
    public Set<String> groupUsersIdentity(Packet packet) {
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        // score 存储的是用户加入群的时间戳，毫秒
        return  (Set<String>) redisTemplate.opsForZSet().range(CacheConstant.OUYUNC + CacheConstant.APP_KEY + metadata.getAppKey() + CacheConstant.COLON + CacheConstant.GROUP_USERS + message.getTo(), NumberConstant.NUMBER_0, NumberConstant.NUMBER_NEGATIVE_1);
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

}
