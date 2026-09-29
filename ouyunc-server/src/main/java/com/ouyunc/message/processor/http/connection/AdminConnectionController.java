package com.ouyunc.message.processor.http.connection;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.GetHttpRequest;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.IgnoreAuth;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.http.annotation.RequestParam;
import com.ouyunc.message.http.auth.HttpAdminAuth;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;

/**
 * 本节点实时连接查询与强制下线接口。
 * <p>完整连接信息不会写入 Redis，因此响应明确返回 {@code scope=LOCAL_NODE}。
 * 集群运维应逐节点调用，或由上层管理服务聚合各节点结果。</p>
 */
@HttpRestController
@HttpRequestMapping
@IgnoreAuth
public class AdminConnectionController {

    /**
     * 查询某个 appKey 或全部 appKey 的当前有效连接。详情分页返回，租户计数基于完整快照。
     */
    @GetHttpRequest(HttpRequestConstant.HTTP_ADMIN_CONNECTIONS_PATH)
    public HttpResponseResult<AdminConnectionQueryResponse> connections(
            HttpContext httpContext,
            @RequestParam(value = "appKey", required = false) String appKey,
            @RequestParam(value = "current", required = false, defaultValue = "1") Long current,
            @RequestParam(value = "size", required = false, defaultValue = "100") Long size)
            throws HttpPipelineException {
        HttpAuthPrincipal principal = HttpAdminAuth.requireConnectionRead(httpContext);
        AdminConnectionQueryResponse response = LocalConnectionAdminService.query(
                appKey, current == null ? 1L : current, size == null ? 100L : size);
        HttpAdminAuth.audit("connections-query", principal, response.node(), "",
                "ok total=" + response.total(), null);
        return HttpResponseResult.success(response);
    }

    /**
     * 强制关闭匹配连接。空条件会被拒绝；关闭全节点连接必须显式传 {@code all=true}。
     */
    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_CONNECTIONS_OFFLINE_PATH)
    public HttpResponseResult<AdminConnectionOfflineResponse> offline(
            HttpContext httpContext,
            @RequestBody AdminConnectionOfflineRequest request) throws HttpPipelineException {
        HttpAuthPrincipal principal = HttpAdminAuth.requireConnectionOffline(httpContext);
        if (!LocalConnectionAdminService.hasEffectiveOfflineCondition(request)) {
            throw com.ouyunc.message.http.auth.HttpJwtAuth.badRequest(
                    "至少提供 appKey、identity、channelId 之一；关闭本节点全部连接请显式传 all=true");
        }
        if (LocalConnectionAdminService.hasConflictingAllCondition(request)) {
            throw com.ouyunc.message.http.auth.HttpJwtAuth.badRequest(
                    "all=true 表示关闭本节点全部连接，不能同时提供 appKey、identity、deviceType 或 channelId");
        }
        String reason = HttpAdminAuth.resolveReason(httpContext, request.getReason());
        try {
            AdminConnectionOfflineResponse response = LocalConnectionAdminService.offline(request);
            HttpAdminAuth.audit("connections-offline", principal, response.node(), reason,
                    "ok matched=" + response.matched() + " closeRequested=" + response.closeRequested(), null);
            return HttpResponseResult.success(response);
        } catch (RuntimeException error) {
            HttpAdminAuth.audit("connections-offline", principal,
                    com.ouyunc.message.context.MessageServerContext.serverProperties().getLocalServerAddress(),
                    reason, "fail", error.getMessage());
            throw error;
        }
    }
}
