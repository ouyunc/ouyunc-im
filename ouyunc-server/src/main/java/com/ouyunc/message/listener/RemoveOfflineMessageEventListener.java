package com.ouyunc.message.listener;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.DeviceType;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.cache.config.CacheFactory;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.repository.support.QosAckContentParser;
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
 * 接收方 C2S ACK 后，从待确认离线 ZSet 移除对应消息。
 * QoS 幂等键仅依赖 TTL 过期，不在此删除，避免发送方在幂等窗口内重试被误判为新消息。
 */
@EventListener(ring = EventRingEnum.REMOVE_OFFLINE_MESSAGE)
class RemoveOfflineMessageEventListener implements MessageEventListener<MessageEvent> {

    private static final Logger log = LoggerFactory.getLogger(RemoveOfflineMessageEventListener.class);

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
                String ackReceiver = message.getFrom();
                if (ackReceiver == null || message.getContent() == null || message.getMetadata() == null) {
                    log.warn("移除离线消息失败：核心参数为空，packet={}", packet);
                    return;
                }

                long ackPacketId = QosAckContentParser.resolveAckPacketId(message.getContent());
                if (ackPacketId <= 0) {
                    log.warn("移除离线消息失败：无法解析 ackId，content={}", message.getContent());
                    return;
                }

                Metadata metadata = message.getMetadata();
                String appKey = metadata.getAppKey();
                DeviceType deviceType = MessageServerContext.deviceType(appKey, packet.getDeviceType());

                RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
                String zSetKey = CacheConstant.buildToOfflineCacheKey(appKey, ackReceiver, deviceType.getType());
                String zSetValue = MessageContext.idGenerator().formatLongId19Str(ackPacketId);

                redisTemplate.executePipelined(new SessionCallback<>() {
                    @Override
                    public <K, V> Boolean execute(RedisOperations<K, V> operations) throws DataAccessException {
                        byte[] zSetKeyBytes = stringSerializer.serialize(zSetKey);
                        byte[] zSetValueBytes = stringSerializer.serialize(zSetValue);
                        if (zSetKeyBytes == null || zSetValueBytes == null) {
                            log.warn("移除离线消息失败：ZSet序列化为空，zSetKey={}", zSetKey);
                            return false;
                        }

                        RedisTemplate<?, ?> rt = (RedisTemplate<?, ?>) operations;
                        RedisConnection conn = Objects.requireNonNull(rt.getConnectionFactory()).getConnection();
                        Long zRemCount = conn.zSetCommands().zRem(zSetKeyBytes, zSetValueBytes);
                        boolean zSetDeleted = zRemCount != null && zRemCount > 0;
                        if (!zSetDeleted) {
                            log.debug("ZSet元素不存在或已删除，key={}, value={}", zSetKey, zSetValue);
                        }
                        return zSetDeleted;
                    }
                });
            } catch (Exception e) {
                log.error("移除离线消息整体流程异常", e);
            }
        }
    }

}
