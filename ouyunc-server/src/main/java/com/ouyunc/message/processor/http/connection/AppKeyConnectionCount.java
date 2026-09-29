package com.ouyunc.message.processor.http.connection;

/** 某个 appKey 在当前节点上的有效连接数。 */
public record AppKeyConnectionCount(String appKey, long connectionCount) {
}
