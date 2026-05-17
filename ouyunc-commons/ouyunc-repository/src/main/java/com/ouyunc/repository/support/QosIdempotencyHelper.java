package com.ouyunc.repository.support;

import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.data.redis.serializer.RedisSerializer;

import java.util.List;

/**
 * QoS 幂等（packetId + 通道身份/客户端 messageId）读写
 */
public final class QosIdempotencyHelper {

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

    /**
     * 抢占幂等键（SET NX）。packet 层失败则视为重复；cli 层失败不阻断（packet 已占位）。
     */
    public static boolean tryClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                   String loginIdentity, String clientMessageId) {
        RedisSerializer<String> stringSerializer = redisTemplate.getStringSerializer();
        byte[] pktKey = stringSerializer.serialize(CacheConstant.buildQosIdempotencyPacketKey(appKey, packetId));
        byte[] pktValue = stringSerializer.serialize(MessageConstant.ONE_STR);
        if (pktKey == null || pktValue == null) {
            return false;
        }
        Boolean pktClaimed = redisTemplate.execute((RedisCallback<Boolean>) connection ->
                connection.stringCommands().set(pktKey, pktValue,
                        Expiration.milliseconds(MessageConstant.CACHE_QOS_IDEM_PACKET_EXPIRE_TIMESTAMP),
                        RedisStringCommands.SetOption.SET_IF_ABSENT));
        if (!Boolean.TRUE.equals(pktClaimed)) {
            return false;
        }
        if (StringUtils.isNotBlank(loginIdentity) && StringUtils.isNotBlank(clientMessageId)) {
            byte[] cliKey = stringSerializer.serialize(
                    CacheConstant.buildQosIdempotencyClientKey(appKey, loginIdentity, clientMessageId));
            byte[] cliValue = stringSerializer.serialize(MessageConstant.ONE_STR);
            if (cliKey != null && cliValue != null) {
                redisTemplate.execute((RedisCallback<Boolean>) connection ->
                        connection.stringCommands().set(cliKey, cliValue,
                                Expiration.milliseconds(MessageConstant.CACHE_QOS_IDEM_CLIENT_EXPIRE_TIMESTAMP),
                                RedisStringCommands.SetOption.SET_IF_ABSENT));
            }
        }
        return true;
    }

    public static void releaseClaim(RedisTemplate<String, ?> redisTemplate, String appKey, long packetId,
                                    String loginIdentity, String clientMessageId) {
        redisTemplate.delete(CacheConstant.buildQosIdempotencyPacketKey(appKey, packetId));
        if (StringUtils.isNotBlank(loginIdentity) && StringUtils.isNotBlank(clientMessageId)) {
            redisTemplate.delete(CacheConstant.buildQosIdempotencyClientKey(appKey, loginIdentity, clientMessageId));
        }
    }
}
