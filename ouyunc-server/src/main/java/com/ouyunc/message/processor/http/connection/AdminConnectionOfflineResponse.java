package com.ouyunc.message.processor.http.connection;

/** 强制下线操作结果；关闭为异步请求，最终释放由 Channel 关闭回调完成。 */
public record AdminConnectionOfflineResponse(
        String scope,
        String node,
        int matched,
        int closeRequested,
        int skippedInactive) {
}
