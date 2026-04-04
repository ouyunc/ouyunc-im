package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpFileResponse;
import com.ouyunc.base.model.HttpRawResponse;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.message.context.MessageServerContext;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求分发：按 method + path 查表调用路由（{@link HttpRestController} 方法或旧版 {@link HttpRequestProcessor}），404/异常时写 JSON。
 */
public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);
    private static final String DEFAULT_HTTP_HANDLER_SCAN_PACKAGE = "com.ouyunc.message.processor";

    private static final HttpRequestDispatcher INSTANCE = new HttpRequestDispatcher();

    private final HttpRouteRegistry routeRegistry = new HttpRouteRegistry();

    private HttpRequestDispatcher() {
        List<String> packages = null;
        if (MessageServerContext.serverProperties() != null) {
            packages = MessageServerContext.serverProperties().getHttpProcessorScanPackagePaths();
        }
        if (CollectionUtils.isEmpty(packages)) {
            packages = Collections.singletonList(DEFAULT_HTTP_HANDLER_SCAN_PACKAGE);
        }
        for (String basePackage : packages) {
            routeRegistry.scanAndRegister(basePackage.trim());
        }
    }

    /**
     * 在服务启动完成时显式调用一次，打印已扫描注册的 HTTP 路由；不在类加载或首次 HTTP 请求时打印。
     */
    public static void logRegisteredHttpRoutesOnStartup() {
        getInstance().routeRegistry.logStartupRouteSummary();
    }

    public static HttpRequestDispatcher getInstance() {
        return INSTANCE;
    }

    public void register(String method, String path, HttpRequestProcessor<?> handler) {
        routeRegistry.registerLegacy(method, path, handler);
    }

    /**
     * 分发 FullHttpRequest：仅当 msg 为 FullHttpRequest 时调用。
     * <p>
     * 每次调用在 {@code finally} 中，若本方法入口 {@code log.isDebugEnabled()} 为 true 则打 DEBUG：HTTP 方法、path（不含 query）、耗时（自进入到本次线程内写出提交完成，非对端收齐响应的 RTT）。
     */
    public void dispatch(ChannelHandlerContext ctx, FullHttpRequest request) {
        HttpContext httpContext = null;
        final boolean logTiming = log.isDebugEnabled();
        final long startNanos = logTiming ? System.nanoTime() : 0L;
        final String method = request.method().name();
        final String path = HttpUtil.pathFromUri(request.uri());
        try {
            HttpRouteMatch match = routeRegistry.find(method, path);
            if (match == null) {
                HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, HttpResponseResult.fail(HttpResponseCodeEnum.NOT_FOUND));
                return;
            }
            httpContext = HttpRequestPipeline.prepare(ctx, request, match.getRoute().getDescriptor(), match.getPathVariables());
            Object result = match.getRoute().getProcessor().process(httpContext);
            if (result instanceof HttpRawResponse raw) {
                HttpUtil.writeRawResponse(ctx, request, raw);
            } else if (result instanceof HttpFileResponse file) {
                HttpUtil.writeFileResponse(ctx, request, file);
            } else {
                HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.OK, HttpResponseResult.success(result));
            }
        } catch (HttpPipelineException e) {
            HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage()));
        } catch (Exception e) {
            log.error("HTTP dispatch error, uri={}", request.uri(), e);
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage()));
        } finally {
            if (logTiming) {
                long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
                log.debug("HTTP {} {} 耗时 {} ms", method, path, costMs);
            }
            if (httpContext != null) {
                httpContext.releaseResources();
            }
        }
    }

}
