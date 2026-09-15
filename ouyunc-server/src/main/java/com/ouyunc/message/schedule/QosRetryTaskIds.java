package com.ouyunc.message.schedule;

import org.apache.commons.lang3.StringUtils;

/**
 * QoS SERVER 重试任务 ID：绑定 appKey、packetId、recipient、deviceType，
 * 避免群发/多端共用 packetId 互相覆盖，也避免任意 ACK 误取消他人重试。
 */
public final class QosRetryTaskIds {

    private static final char SEP = ':';

    private QosRetryTaskIds() {
    }

    public static String build(String appKey, long packetId, String recipientId, byte deviceType) {
        if (StringUtils.isAnyBlank(appKey, recipientId) || packetId <= 0) {
            return null;
        }
        return appKey + SEP + packetId + SEP + recipientId + SEP + deviceType;
    }

    public static String build(QosRetryTaskContext context) {
        if (context == null) {
            return null;
        }
        return build(context.appKey(), context.packetId(), context.targetIdentity(), context.deviceType());
    }
}
