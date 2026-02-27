package com.ouyunc.domain.entity;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

/**
 * mongodb message
 */
public class MongoMessageEntity extends MessageEntity {
    /**
     * 过期时间
     */
    @Field("expire_at")
    @Indexed(expireAfter = "0s")
    private LocalDateTime expireAt;




    public MongoMessageEntity() {
    }


    public MongoMessageEntity(long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, byte retain, String clientIp, String messageId, String from, int fromType, String to, int toType, int contentType, String content, int qos, String at, String ref, String extra, long clientSendTime, long serverArrivalTime, String appKey, LocalDateTime expireAt) {
        super(id, protocol, protocolVersion, deviceType, networkType, encryptType, serializeAlgorithm, messageType, retain, clientIp, messageId, from, fromType, to, toType, contentType, content, qos, at, ref, extra, clientSendTime, serverArrivalTime, appKey);
        this.expireAt = expireAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}