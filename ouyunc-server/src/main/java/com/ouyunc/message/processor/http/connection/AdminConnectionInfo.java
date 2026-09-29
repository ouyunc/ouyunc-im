package com.ouyunc.message.processor.http.connection;

/**
 * 运维接口返回的本节点连接快照。
 * <p>只包含定位和诊断连接所需的信息，不返回登录签名、遗嘱消息等敏感字段。</p>
 */
public record AdminConnectionInfo(
        String channelId,
        String appKey,
        String identity,
        byte deviceType,
        String node,
        long nodeEpoch,
        long lastLoginTime,
        long loginCreateTime,
        String clientRealIp,
        String remoteAddress,
        String localAddress,
        byte protocol,
        byte protocolVersion,
        int loginScope,
        int heartBeatTimeoutSeconds,
        boolean active,
        boolean writable) {
}
