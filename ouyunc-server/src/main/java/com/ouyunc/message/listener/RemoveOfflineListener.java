package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.QosAckContent;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SessionCallback;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.Objects;

/**
 * 异常离线消息监听器（所有qos > 0 的消息都应该进入每个客户端的离线队列中（待确认队列中），包括群聊和私聊模式的业务，这里将离线队列当做待确认队列使用）
 * 会话消息会实时保存全量聊天数据。离线消息的存在只是在某种业务上，单聊的会话消息不对外开放，客户端只能通过离线消息和实时获取的消息来展现；
 * 然而针对群组类的业务，考虑到服务器的压力以及多种做法，采用拉取模式来根据需要获取群组消息，定时获取群组消息，或者服务端通知客户端有群组消息，让客户端按需拉取群组消息，减少服务端压力；
 * 当然拉取的服务可以是其他业务服务器，这样减少了IM 服务器端的压力；
 * 注意：待接收确认消息，归并到离线消息中，所以收到qos的消息确认接收事件，就会从离线消息中删除已确认接收的数据；
 * 离线消息使用redis 的zset 数据结构来存储，目前只存储单聊的消息（推模式），对于群组类的业务（一般使用拉取模式，按需拉取）数据不进行存储，
 * 离线消息一般存储是有时间限制的，比如存储在离线消息的过期时间是7天，所以要启动一个定时任务定时去删除过期的离线消息
 */
public class RemoveOfflineListener implements MessageListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(RemoveOfflineListener.class);

    private static final RedisTemplate redisTemplate = CacheFactory.REDIS.instance();

    @SuppressWarnings("unchecked")
    @Override
    public EventType type() {
        return MessageEventTypeEnum.REMOVE_OFFLINE;
    }

    @Override
    public void onEvent(MessageEvent event) {
        Object source = event.getSource();
        log.debug("移除离线消息监听器正在处理：{}", event.getSource());
        if (source instanceof Packet packet) {
            try {
                Message message = packet.getMessage();
                String from = message.getFrom();
                // 空值校验：核心参数为空直接返回
                if (from == null || message.getContent() == null || message.getMetadata() == null) {
                    log.warn("移除离线消息失败：核心参数为空，packet={}", packet);
                    return;
                }

                QosAckContent qosAckContent = JSON.parseObject(message.getContent(), QosAckContent.class);
                // 校验反序列化结果
                if (qosAckContent == null || qosAckContent.getAckId() == null || qosAckContent.getMessageId() == null) {
                    log.warn("移除离线消息失败：QosAckContent解析异常，content={}", message.getContent());
                    return;
                }

                Metadata metadata = message.getMetadata();
                DeviceType deviceType = MessageServerContext.deviceType(metadata.getAppKey(), packet.getDeviceType());
                // 校验设备类型

                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();

                // 使用SessionCallback执行批量原子操作，返回操作结果
                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public <K, V> Boolean execute(RedisOperations<K, V> operations) throws DataAccessException {

                        // ========== 1. 构建并序列化所有Key/Value ==========
                        // ZSet相关
                        String zSetKey = CacheConstant.buildToOfflineCacheKey(metadata.getAppKey(), from, deviceType.getType());
                        String zSetValue = MessageContext.idGenerator().formatLongId19Str(qosAckContent.getAckId());
                        byte[] zSetKeyBytes = stringSerializer.serialize(zSetKey);
                        byte[] zSetValueBytes = stringSerializer.serialize(zSetValue);

                        // Hash相关
                        String hashKey = CacheConstant.buildFromOfflineCacheKey(metadata.getAppKey(), from);
                        String hashField = qosAckContent.getMessageId();

                        // 空值校验：序列化失败直接返回false
                        if (zSetKeyBytes == null || zSetValueBytes == null || hashKey == null || hashField == null) {
                            log.warn("移除离线消息失败：序列化为空，zSetKey={}, zSetValue={}, hashKey={}, hashField={}",
                                    zSetKey, zSetValue, hashKey, hashField);
                            return false;
                        }

                        RedisTemplate redisTemplate = (RedisTemplate) operations;
                        // ========== 2. 执行ZSet删除操作 ==========
                        RedisConnection conn = Objects.requireNonNull(redisTemplate.getConnectionFactory()).getConnection();
                        Long zRemCount = conn.zSetCommands().zRem(zSetKeyBytes, zSetValueBytes);
                        boolean zSetDeleted = zRemCount != null && zRemCount > 0;
                        if (!zSetDeleted) {
                            log.info("ZSet元素不存在或删除失败，key={}, value={}", zSetKey, zSetValue);
                        }

                        // ========== 3. 执行Hash删除操作 ==========
                        boolean hashDeleted = operations.opsForHash().delete((K) hashKey, hashField) > 0;
                        if (!hashDeleted) {
                            log.info("Hash字段不存在或删除失败，key={}, field={}", hashKey, hashField);
                        }

                        // 返回整体结果：两个操作至少有一个成功（或根据业务需求改为"都成功"）
                        return zSetDeleted || hashDeleted;


                    }
                });
            } catch (Exception e) {
                log.error("移除离线消息整体流程异常", e);
            }
        }
    }

}
