package com.ouyunc.message.schedule;

/**
 * QoS SERVER 重试任务上下文（仅存索引字段，重推时从 Redis/DB 加载 {@link com.ouyunc.base.packet.Packet}）。
 */
public record QosRetryTaskContext(String appKey, long packetId, String targetAppKey, String targetIdentity) {
}
