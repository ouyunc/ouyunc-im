package com.ouyunc.message.processor.http.connection;

import java.util.List;

/**
 * 本节点连接分页响应。
 * <p>分页字段与 MyBatis-Plus {@code IPage/Page} 常用 JSON 结构一致：
 * {@code records/total/size/current/pages}。连接数据来自内存 Channel 注册表，
 * 不引入数据库分页拦截器。</p>
 *
 * @param scope 固定为 {@code LOCAL_NODE}，用于防止调用方把结果误认为集群全局连接
 */
public record AdminConnectionQueryResponse(
        String scope,
        String node,
        long snapshotTime,
        List<AppKeyConnectionCount> appKeyCounts,
        List<AdminConnectionInfo> records,
        long total,
        long size,
        long current,
        long pages) {

    public static final String SCOPE_LOCAL_NODE = "LOCAL_NODE";
}
