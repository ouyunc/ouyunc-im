package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.model.HttpFileResponse;
import com.ouyunc.base.model.HttpRawResponse;
import com.ouyunc.base.model.HttpResponseResult;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.EventLoop;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.concurrent.BasicThreadFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求分发：按 method + path 查表调用路由（{@link HttpRestController} 方法或旧版 {@link HttpRequestProcessor}），404/异常时写 JSON。
 * <p>
 * 可通过 {@code ouyunc.message.http.business-executor-threads} 将 prepare + process 放到业务线程池，避免阻塞 Netty EventLoop；
 * 响应写入始终在 Channel 的 EventLoop 上执行。
 */
public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);
    private static final String DEFAULT_HTTP_HANDLER_SCAN_PACKAGE = "com.ouyunc.message.processor";

    private static final HttpRequestDispatcher INSTANCE = new HttpRequestDispatcher();

    private final HttpRouteRegistry routeRegistry = new HttpRouteRegistry();

    private final Object httpExecutorLock = new Object();
    private volatile EventExecutorGroup httpBusinessExecutor;

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

    /**
     * 优雅关闭 HTTP 业务线程池（在 Netty 关闭前调用）；未创建或未启用时无操作。
     */
    public static void shutdownHttpBusinessExecutor() {
        EventExecutorGroup g = INSTANCE.httpBusinessExecutor;
        if (g != null) {
            g.shutdownGracefully();
        }
    }

    public void register(String method, String path, HttpRequestProcessor<?> handler) {
        routeRegistry.registerLegacy(method, path, handler);
    }

    /**
     * 分发 FullHttpRequest：仅当 msg 为 FullHttpRequest 时调用。
     * <p>
     * 同步模式下在 {@code finally} 中打 DEBUG 耗时；异步模式下在完成写出后的 EventLoop 任务中打 DEBUG。
     */
    public void dispatch(ChannelHandlerContext ctx, FullHttpRequest request) {
        final boolean logTiming = log.isDebugEnabled();
        final long startNanos = logTiming ? System.nanoTime() : 0L;
        final String method = request.method().name();
        final String path = HttpUtil.pathFromUri(request.uri());

        HttpRouteMatch match = routeRegistry.find(method, path);
        if (match == null) {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, HttpResponseResult.fail(HttpResponseCodeEnum.NOT_FOUND));
            logTimingLine(logTiming, startNanos, method, path);
            return;
        }

        EventExecutorGroup biz = resolveHttpBusinessExecutor();
        if (biz == null) {
            dispatchSync(ctx, request, match, logTiming, startNanos, method, path);
        } else {
            dispatchAsync(ctx, request, match, biz, logTiming, startNanos, method, path);
        }
    }

    private void dispatchSync(ChannelHandlerContext ctx, FullHttpRequest request, HttpRouteMatch match,
                              boolean logTiming, long startNanos, String method, String path) {
        HttpContext httpContext = null;
        try {
            httpContext = HttpRequestPipeline.prepare(ctx, request, match.getRoute().getDescriptor(), match.getPathVariables());
            Object result = match.getRoute().getProcessor().process(httpContext);
            writeDispatchResult(ctx, request, result);
        } catch (HttpPipelineException e) {
            HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage()));
        } catch (Exception e) {
            log.error("HTTP dispatch error, uri={}", request.uri(), e);
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
        } finally {
            logTimingLine(logTiming, startNanos, method, path);
            if (httpContext != null) {
                httpContext.releaseResources();
            }
        }
    }

    private void dispatchAsync(ChannelHandlerContext ctx, FullHttpRequest request, HttpRouteMatch match, EventExecutorGroup biz,
                               boolean logTiming, long startNanos, String method, String path) {
        request.retain();
        biz.execute(() -> {
            HttpContext httpContext = null;
            try {
                httpContext = HttpRequestPipeline.prepare(ctx, request, match.getRoute().getDescriptor(), match.getPathVariables());
                Object result = match.getRoute().getProcessor().process(httpContext);
                final HttpContext hc = httpContext;
                final Object fr = result;
                runOnChannelEventLoop(ctx, request, hc, logTiming, startNanos, method, path, () -> {
                    try {
                        writeDispatchResult(ctx, request, fr);
                    } catch (Exception e) {
                        log.error("HTTP write response error, uri={}", request.uri(), e);
                        HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
                    }
                });
            } catch (HttpPipelineException e) {
                final HttpContext hc = httpContext;
                runOnChannelEventLoop(ctx, request, hc, logTiming, startNanos, method, path, () ->
                        HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage())));
            } catch (Exception e) {
                final HttpContext hc = httpContext;
                runOnChannelEventLoop(ctx, request, hc, logTiming, startNanos, method, path, () -> {
                    log.error("HTTP dispatch error, uri={}", request.uri(), e);
                    HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                            HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
                });
            }
        });
    }

    private static void runOnChannelEventLoop(ChannelHandlerContext ctx, FullHttpRequest request, HttpContext httpContext,
                                              boolean logTiming, long startNanos, String method, String path, Runnable writeOnEventLoop) {
        EventLoop eventLoop = ctx.channel().eventLoop();
        if (eventLoop.isShutdown()) {
            if (httpContext != null) {
                httpContext.releaseResources();
            }
            request.release();
            return;
        }
        eventLoop.execute(() -> {
            try {
                writeOnEventLoop.run();
            } finally {
                if (httpContext != null) {
                    httpContext.releaseResources();
                }
                request.release();
                logTimingLine(logTiming, startNanos, method, path);
            }
        });
    }

    private static void writeDispatchResult(ChannelHandlerContext ctx, FullHttpRequest request, Object result) throws Exception {
        if (result instanceof HttpRawResponse raw) {
            HttpUtil.writeRawResponse(ctx, request, raw);
        } else if (result instanceof HttpFileResponse file) {
            HttpUtil.writeFileResponse(ctx, request, file);
        } else {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.OK, HttpResponseResult.success(result));
        }
    }

    private static void logTimingLine(boolean logTiming, long startNanos, String method, String path) {
        if (logTiming) {
            long costMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startNanos);
            log.debug("HTTP {} {} 耗时 {} ms", method, path, costMs);
        }
    }

    private EventExecutorGroup resolveHttpBusinessExecutor() {
        MessageServerProperties p = MessageServerContext.serverProperties();
        int threads = p != null ? p.getHttpBusinessExecutorThreads() : 0;
        if (threads <= 0) {
            return null;
        }
        if (httpBusinessExecutor == null) {
            synchronized (httpExecutorLock) {
                if (httpBusinessExecutor == null) {
                    httpBusinessExecutor = new DefaultEventExecutorGroup(threads,
                            new BasicThreadFactory.Builder().namingPattern("http-business-%d").daemon(true).build());
                }
            }
        }
        return httpBusinessExecutor;
    }

}
