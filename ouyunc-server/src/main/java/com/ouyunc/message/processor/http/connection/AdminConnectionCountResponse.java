package com.ouyunc.message.processor.http.connection;

import java.util.List;

/**
 * 本节点有效连接数快照。
 *
 * @param totalConnections 符合 appKey 过滤条件的有效连接总数
 * @param appKeyCounts     按 appKey 聚合的连接数；指定 appKey 时最多一项
 */
public record AdminConnectionCountResponse(
        String scope,
        String node,
        long snapshotTime,
        long totalConnections,
        List<AppKeyConnectionCount> appKeyCounts) {
}
