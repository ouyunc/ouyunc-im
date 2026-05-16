package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;
import java.util.Objects;

/**
 * QoS 幂等（packetId + 通道身份/客户端 messageId）读写
 */
public final class QosIdempotencyHelper {

    private static final byte[] MARK_VALUE = "1".getBytes();

    private QosIdempotencyHelper() {
    }

    public static boolean isDuplicate(RedisTemplate<String, ?> redisTemplate, Packet packet, String channelLoginIdentity) {
        if (packet == null || packet.getMessage() == null) {
            return false;
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        if (metadata == null || metadata.getAppKey() == null) {
            return false;
        }
        String appKey = metadata.getAppKey();
        long packetId = packet.getPacketId();
        boolean checkPacket = packetId > 0;
        boolean checkClient = StringUtils.isNotBlank(channelLoginIdentity) && StringUtils.isNotBlank(message.getId());
        if (!checkPacket && !checkClient) {
            return false;
        }

        RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
        byte[] pktKey = checkPacket
                ? stringSerializer.serialize(CacheConstant.buildQosIdempotencyPacketKey(appKey, packetId)) : null;
        byte[] cliKey = checkClient
                ? stringSerializer.serialize(CacheConstant.buildQosIdempotencyClientKey(appKey, channelLoginIdentity, message.getId()))
                : null;

        List<Object> existsResults = redisTemplate.executePipelined((RedisCallback<Object>) connection -> {
            if (pktKey != null) {
                connection.keyCommands().exists(pktKey);
            }
            if (cliKey != null) {
                connection.keyCommands().exists(cliKey);
            }
            return null;
        });

        int idx = 0;
        if (pktKey != null && idx < existsResults.size()) {
            if (Boolean.TRUE.equals(existsResults.get(idx++))) {
                return true;
            }
        }
        if (cliKey != null && idx < existsResults.size()) {
            return Boolean.TRUE.equals(existsResults.get(idx));
        }
        return false;
    }

    public static void markOnConnection(RedisConnection conn, RedisSerializer<String> stringSerializer,
                                        String appKey, long packetId, String loginIdentity, String clientMessageId) {
        byte[] pktKey = serialize(stringSerializer, CacheConstant.buildQosIdempotencyPacketKey(appKey, packetId));
        if (pktKey != null) {
            conn.commands().set(pktKey, MARK_VALUE);
            conn.keyCommands().pExpire(pktKey, MessageConstant.CACHE_QOS_IDEM_PACKET_EXPIRE_TIMESTAMP);
        }
        if (StringUtils.isNotBlank(loginIdentity) && StringUtils.isNotBlank(clientMessageId)) {
            byte[] cliKey = serialize(stringSerializer,
                    CacheConstant.buildQosIdempotencyClientKey(appKey, loginIdentity, clientMessageId));
            if (cliKey != null) {
                conn.commands().set(cliKey, MARK_VALUE);
                conn.keyCommands().pExpire(cliKey, MessageConstant.CACHE_QOS_IDEM_CLIENT_EXPIRE_TIMESTAMP);
            }
        }
    }

    private static byte[] serialize(RedisSerializer<String> serializer, String key) {
        if (key == null) {
            return null;
        }
        return Objects.requireNonNullElse(serializer.serialize(key), null);
    }
}
