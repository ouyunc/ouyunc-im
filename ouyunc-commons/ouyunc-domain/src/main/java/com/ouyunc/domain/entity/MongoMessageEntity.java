package com.ouyunc.domain.entity;

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
    private LocalDateTime expireAt;




    public MongoMessageEntity() {
    }

    public MongoMessageEntity(long id, byte protocol, byte protocolVersion, byte deviceType, byte networkType, byte encryptType, byte serializeAlgorithm, byte messageType, byte retain, String clientIp, String from, String to, int contentType, String content, int qos, String at, String extra, long clientSendTime, long serverArrivalTime, int read, int withdrawn, String appKey, LocalDateTime expireAt) {
        super(id, protocol, protocolVersion, deviceType, networkType, encryptType, serializeAlgorithm, messageType, retain, clientIp, from, to, contentType, content, qos, at, extra, clientSendTime, serverArrivalTime, read, withdrawn, appKey);
        this.expireAt = expireAt;
    }

    public LocalDateTime getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(LocalDateTime expireAt) {
        this.expireAt = expireAt;
    }
}