package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.ServerDrainStatusResponse;
import com.ouyunc.base.model.ServerKickClientsResponse;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.helper.ClientHelper;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.PostHttpRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 运维摘流 / 通知重连接口（需 {@code X-App-Key} 鉴权）。
 * <p>
 * 滚动升级推荐顺序：Nginx 摘 upstream → {@code POST /admin/drain} →
 * {@code POST /admin/kick-clients}（仅通知，由客户端主动断开重连）→ 停进程发版 → 挂回 LB。
 */
@HttpRestController
@HttpRequestMapping
public class AdminDrainController {

    private static final Logger log = LoggerFactory.getLogger(AdminDrainController.class);

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_DRAIN_PATH)
    public HttpResponseResult<ServerDrainStatusResponse> drain() {
        MessageServerContext.enterDrainMode();
        return HttpResponseResult.success(buildDrainStatus());
    }

    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_UNDRAIN_PATH)
    public HttpResponseResult<ServerDrainStatusResponse> undrain() {
        MessageServerContext.exitDrainMode();
        return HttpResponseResult.success(buildDrainStatus());
    }

    /**
     * 通知本机全部在线客户端主动断开并重连其他节点。
     * <p>会先进入摘流；服务端不主动 close，由客户端收到 SERVER_NOTIFY 后自行断开。
     * 本接口不停止 JVM 进程。
     */
    @PostHttpRequest(HttpRequestConstant.HTTP_ADMIN_KICK_CLIENTS_PATH)
    public HttpResponseResult<ServerKickClientsResponse> kickClients() {
        MessageServerContext.enterDrainMode();
        int notified = ClientHelper.notifyAllLocalClientsToReconnect();
        int localOnlineRemaining = MessageServerContext.localLoginClientRegisterTable.asMap().size();
        log.warn("HTTP 运维通知客户端主动重连完成, notified={}, remaining={}, address={}",
                notified, localOnlineRemaining, MessageServerContext.serverProperties().getLocalServerAddress());
        ServerKickClientsResponse body = ServerKickClientsResponse.of(
                buildDrainStatus(), notified, localOnlineRemaining);
        return HttpResponseResult.success(body);
    }

    private static ServerDrainStatusResponse buildDrainStatus() {
        return ServerDrainStatusResponse.of(
                MessageServerContext.DRAINING.get(),
                MessageServerContext.ACCEPT_NEW_CONNECTIONS.get(),
                MessageServerContext.serverProperties().getLocalServerAddress(),
                HttpRequestConstant.HTTP_READY_PATH);
    }
}
