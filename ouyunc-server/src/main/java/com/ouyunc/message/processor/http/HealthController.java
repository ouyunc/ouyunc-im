package com.ouyunc.message.processor.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.model.ServerHealthResponse;
import com.ouyunc.base.model.ServerReadyResponse;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.http.HttpPipelineException;
import com.ouyunc.message.http.annotation.GetHttpRequest;
import com.ouyunc.message.http.annotation.HttpRequestMapping;
import com.ouyunc.message.http.annotation.HttpRestController;
import com.ouyunc.message.http.annotation.IgnoreAuth;
import com.ouyunc.message.monitor.ResourceMonitor;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * LB 探活：与 IM 同端口（默认 6003）。
 * <ul>
 *   <li>{@code GET /health}：存活，进程在即 200</li>
 *   <li>{@code GET /ready}：就绪；摘流或存在严重资源问题时 HTTP 503</li>
 * </ul>
 */
@IgnoreAuth
@HttpRestController
@HttpRequestMapping
public class HealthController {

    @GetHttpRequest(HttpRequestConstant.HTTP_HEALTH_PATH)
    public HttpResponseResult<ServerHealthResponse> health() {
        return HttpResponseResult.success(ServerHealthResponse.up(
                MessageServerContext.serverProperties().getLocalServerAddress(),
                MessageServerContext.DRAINING.get()));
    }

    @GetHttpRequest(HttpRequestConstant.HTTP_READY_PATH)
    public HttpResponseResult<ServerReadyResponse> ready() throws HttpPipelineException {
        if (!MessageServerContext.isAcceptingNewConnections()) {
            throw new HttpPipelineException(
                    HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseCodeEnum.SERVICE_UNAVAILABLE,
                    "not ready (drain or accept-new-connections=false)");
        }
        ResourceMonitor.HealthCheckResult health = ResourceMonitor.checkHealth();
        if (!health.getIssues().isEmpty()) {
            throw new HttpPipelineException(
                    HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseCodeEnum.SERVICE_UNAVAILABLE,
                    "unhealthy: " + health.getIssues());
        }
        return HttpResponseResult.success(ServerReadyResponse.ready(
                MessageServerContext.serverProperties().getLocalServerAddress(),
                health.getWarnings()));
    }
}
