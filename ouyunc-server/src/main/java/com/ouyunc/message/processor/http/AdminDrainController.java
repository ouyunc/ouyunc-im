package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.ServerDrainStatusResponse;
import com.ouyunc.base.model.ServerKickClientsResponse;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.http.HttpContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import com.ouyunc.message.http.annotation.RequestParam;
import com.ouyunc.message.http.auth.HttpAdminAuth;
import com.ouyunc.message.http.auth.HttpAuthPrincipal;

/**
 * 运维摘流 / 通知重连接口。默认关闭；开启后需独立运维 JWT（scope=im:admin:drain）。
 * <p>
 * 滚动升级推荐顺序：Nginx 摘 upstream → {@code POST /api/im/admin/drain} →
 * {@code POST /api/im/admin/kick-clients}（仅通知，由客户端主动断开重连）→ 停进程发版 → 挂回 LB。
 */
@HttpRestController
@HttpRequestMapping
public class AdminDrainController {

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_DRAIN_PATH)
    public HttpResponseResult<ServerDrainStatusResponse> drain(
            HttpContext httpContext,
            @RequestParam(value = "reason", required = false) String reason) throws HttpPipelineException {
        HttpAuthPrincipal principal = HttpAdminAuth.requireDrain(httpContext);
        String node = localNode();
        String auditReason = HttpAdminAuth.resolveReason(httpContext, reason);
        try {
            MessageServerContext.enterAdminDrainMode();
            HttpAdminAuth.audit("drain", principal, node, auditReason, "ok", null);
            return HttpResponseResult.success(buildDrainStatus());
        } catch (RuntimeException e) {
            HttpAdminAuth.audit("drain", principal, node, auditReason, "fail", e.getMessage());
            throw e;
        }
    }

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_UNDRAIN_PATH)
    public HttpResponseResult<ServerDrainStatusResponse> undrain(
            HttpContext httpContext,
            @RequestParam(value = "reason", required = false) String reason) throws HttpPipelineException {
        HttpAuthPrincipal principal = HttpAdminAuth.requireDrain(httpContext);
        String node = localNode();
        String auditReason = HttpAdminAuth.resolveReason(httpContext, reason);
        try {
            MessageServerContext.exitAdminDrainMode();
            HttpAdminAuth.audit("undrain", principal, node, auditReason, "ok", null);
            return HttpResponseResult.success(buildDrainStatus());
        } catch (RuntimeException e) {
            HttpAdminAuth.audit("undrain", principal, node, auditReason, "fail", e.getMessage());
            throw e;
        }
    }

    /**
     * 通知本机全部在线客户端主动断开并重连其他节点。
     * <p>会先进入摘流；服务端不主动 close，由客户端收到 SERVER_NOTIFY 后自行断开。
     * 本接口不停止 JVM 进程。
     */
    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_KICK_CLIENTS_PATH)
    public HttpResponseResult<ServerKickClientsResponse> kickClients(
            HttpContext httpContext,
            @RequestParam(value = "reason", required = false) String reason) throws HttpPipelineException {
        HttpAuthPrincipal principal = HttpAdminAuth.requireDrain(httpContext);
        String node = localNode();
        String auditReason = HttpAdminAuth.resolveReason(httpContext, reason);
        try {
            MessageServerContext.enterAdminDrainMode();
            int notified = ClientHelper.notifyAllLocalClientsToReconnect();
            int localOnlineRemaining = MessageServerContext.localLoginClientRegisterTable.asMap().size();
            HttpAdminAuth.audit("kick-clients", principal, node, auditReason,
                    "ok notified=" + notified + " remaining=" + localOnlineRemaining, null);
            ServerKickClientsResponse body = ServerKickClientsResponse.of(
                    buildDrainStatus(), notified, localOnlineRemaining);
            return HttpResponseResult.success(body);
        } catch (RuntimeException e) {
            HttpAdminAuth.audit("kick-clients", principal, node, auditReason, "fail", e.getMessage());
            throw e;
        }
    }

    private static ServerDrainStatusResponse buildDrainStatus() {
        return ServerDrainStatusResponse.of(
                MessageServerContext.DRAINING.get(),
                MessageServerContext.ACCEPT_NEW_CONNECTIONS.get(),
                localNode(),
                HttpRequestConstant.HTTP_READY_PATH);
    }

    private static String localNode() {
        return MessageServerContext.serverProperties().getLocalServerAddress();
    }
}
