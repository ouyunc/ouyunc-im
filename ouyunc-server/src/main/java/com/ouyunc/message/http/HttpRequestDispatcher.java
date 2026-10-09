package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.constant.MessageConstant;
import com.ouyunc.base.constant.HttpRequestConstant;
import io.netty.util.concurrent.RejectedExecutionHandlers;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
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
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

/**
 * HTTP 请求分发：按 method + path 查表调用路由（{@link HttpRestController} 方法或旧版 {@link HttpRequestProcessor}），404/异常时写 JSON。
 * <p>
 * 可通过 {@code ouyunc.message.http.business-executor-threads} 将 prepare + process 放到业务线程池，避免阻塞 Netty EventLoop；
 * 响应写入始终在 Channel 的 EventLoop 上执行。
 * 若 {@code process} 返回 {@link CompletionStage}，则在完成后再写响应（便于 HTTP 推送 verify 异步化）。
 */
public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);
    private static final String DEFAULT_HTTP_HANDLER_SCAN_PACKAGE = "com.ouyunc.message.processor";

    private static final HttpRequestDispatcher INSTANCE = new HttpRequestDispatcher();

    private final HttpRouteRegistry routeRegistry = new HttpRouteRegistry();

    private final Object httpExecutorLock = new Object();
    private volatile EventExecutorGroup httpBusinessExecutor;
    private volatile boolean stopping;

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

    /** 聚合前拒绝停机后的新请求，已接收请求继续使用原有预算排空。 */
    public static boolean isStopping() {
        return INSTANCE.stopping;
    }

    /**
     * 优雅关闭 HTTP 业务线程池（在 Netty 关闭前调用）；未创建或未启用时无操作。
     */
    public static void shutdownHttpBusinessExecutor() {
        long deadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(MessageConstant.HTTP_DRAIN_TIMEOUT_MS);
        EventExecutorGroup g;
        synchronized (INSTANCE.httpExecutorLock) {
            INSTANCE.stopping = true;
            g = INSTANCE.httpBusinessExecutor;
        }
        try {
            // 先排空已接收的异步业务，Netty 仍保持可写；总等待受同一期限约束。
            if (!HttpRequestAdmission.awaitEmpty(deadline)) {
                log.warn("HTTP 排空超时，剩余请求={}", HttpRequestAdmission.snapshot().inFlight());
            }
            if (g != null) {
                long remaining = Math.max(0, deadline - System.nanoTime());
                g.shutdownGracefully(0, remaining, TimeUnit.NANOSECONDS).await(remaining, TimeUnit.NANOSECONDS);
            }
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            if (g != null && !g.isShuttingDown()) {
                g.shutdownGracefully(0, 0, TimeUnit.MILLISECONDS);
            }
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

        if ("GET".equals(method) && !request.content().isReadable()
                && (HttpRequestConstant.HTTP_HEALTH_PATH.equals(path)
                || HttpRequestConstant.HTTP_READY_PATH.equals(path)
                || HttpRequestConstant.HTTP_WRITE_READY_PATH.equals(path))) {
            // 仅内置只读探活走 EventLoop；普通业务仍必须通过节点预算和业务线程池。
            dispatchSync(ctx, request, match, HttpRequestAdmission.probe(), logTiming, startNanos, method, path);
            return;
        }
        if (stopping) {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseResult.error(HttpResponseCodeEnum.SERVICE_UNAVAILABLE, "HTTP service stopping"));
            return;
        }
        HttpRequestAdmission.Lease admission = com.ouyunc.message.handler.HttpReceiveAdmissionHandler.take(ctx);
        if (admission == null) {
            admission = HttpRequestAdmission.tryAcquire(request);
        }
        if (admission == null) {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseResult.error(HttpResponseCodeEnum.SERVICE_UNAVAILABLE, "HTTP capacity exhausted"));
            return;
        }
        EventExecutorGroup biz;
        try {
            biz = resolveHttpBusinessExecutor();
        } catch (RuntimeException error) {
            admission.close();
            throw error;
        }
        if (biz == null) {
            dispatchSync(ctx, request, match, admission, logTiming, startNanos, method, path);
        } else {
            dispatchAsync(ctx, request, match, biz, admission, logTiming, startNanos, method, path);
        }
    }

    private void dispatchSync(ChannelHandlerContext ctx, FullHttpRequest request, HttpRouteMatch match, HttpRequestAdmission.Lease admission,
                              boolean logTiming, long startNanos, String method, String path) {
        HttpContext httpContext = null;
        boolean deferred = false;
        try {
            httpContext = HttpRequestPipeline.prepare(ctx, request, match.getRoute().getDescriptor(), match.getPathVariables());
            Object result = match.getRoute().getProcessor().process(httpContext);
            if (result instanceof CompletionStage<?> stage) {
                // 异步完成前保留 request；httpContext 所有权交给完成回调
                request.retain();
                final HttpContext hc = httpContext;
                httpContext = null;
                deferred = true;
                completeWhenReady(ctx, request, hc, stage, admission, logTiming, startNanos, method, path);
                return;
            }
            writeDispatchResult(ctx, request, result);
        } catch (HttpPipelineException e) {
            HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage()));
        } catch (Exception e) {
            log.error("HTTP dispatch error, uri={}", request.uri(), e);
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                    HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
        } finally {
            if (!deferred) {
                try {
                    if (httpContext != null) {
                        httpContext.releaseResources();
                    }
                } finally {
                    admission.close();
                    logTimingLine(logTiming, startNanos, method, path);
                }
            }
        }
    }

    private void dispatchAsync(ChannelHandlerContext ctx, FullHttpRequest request, HttpRouteMatch match, EventExecutorGroup biz, HttpRequestAdmission.Lease admission,
                               boolean logTiming, long startNanos, String method, String path) {
        request.retain();
        try {
            biz.execute(() -> {
                if (!ctx.channel().isActive()) {
                    runOnChannelEventLoop(ctx, request, null, admission, logTiming, startNanos, method, path, () -> { });
                    return;
                }
                HttpContext httpContext = null;
                try {
                    httpContext = HttpRequestPipeline.prepare(ctx, request, match.getRoute().getDescriptor(), match.getPathVariables());
                    Object result = match.getRoute().getProcessor().process(httpContext);
                    final HttpContext hc = httpContext;
                    httpContext = null;
                    if (result instanceof CompletionStage<?> stage) {
                        completeWhenReady(ctx, request, hc, stage, admission, logTiming, startNanos, method, path);
                        return;
                    }
                    runOnChannelEventLoop(ctx, request, hc, admission, logTiming, startNanos, method, path, () -> {
                        try {
                            writeDispatchResult(ctx, request, result);
                        } catch (Exception e) {
                            log.error("HTTP write response error, uri={}", request.uri(), e);
                            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                    HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
                        }
                    });
                } catch (HttpPipelineException e) {
                    final HttpContext hc = httpContext;
                    runOnChannelEventLoop(ctx, request, hc, admission, logTiming, startNanos, method, path, () ->
                            HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage())));
                } catch (Exception e) {
                    final HttpContext hc = httpContext;
                    runOnChannelEventLoop(ctx, request, hc, admission, logTiming, startNanos, method, path, () -> {
                        log.error("HTTP dispatch error, uri={}", request.uri(), e);
                        HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                                HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
                    });
                }
            });
        } catch (RejectedExecutionException rejected) {
            // retain 的所有权尚未交给工作线程，提交失败必须立即回收。
            request.release();
            admission.close();
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.SERVICE_UNAVAILABLE,
                    HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP service busy"));
        }
    }


    private static void completeWhenReady(ChannelHandlerContext ctx, FullHttpRequest request, HttpContext httpContext,
                                          CompletionStage<?> stage, HttpRequestAdmission.Lease admission, boolean logTiming, long startNanos,
                                          String method, String path) {
        // 不使用 source.orTimeout：超时只结束响应所有权，不擅自取消业务存储操作。
        // 到期后即使源 stage 迟到，也只能由 gate 的唯一完成回调释放一次资源。
        // 业务与响应各自持有请求引用。响应超时后，异步处理器仍可安全读取请求资源。
        request.retain();
        admission.retainBusiness(() -> {
            try {
                httpContext.releaseResources();
            } finally {
                request.release();
            }
        });
        CompletableFuture<Object> gate = new CompletableFuture<>();
        var closeListener = new io.netty.channel.ChannelFutureListener() {
            @Override
            public void operationComplete(io.netty.channel.ChannelFuture future) {
                gate.completeExceptionally(new java.util.concurrent.CancellationException("HTTP channel closed"));
            }
        };
        ctx.channel().closeFuture().addListener(closeListener);
        gate.orTimeout(MessageConstant.HTTP_ASYNC_RESULT_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        gate.whenComplete((result, error) -> {
            ctx.channel().closeFuture().removeListener(closeListener);
            runOnChannelEventLoop(ctx, request, null, admission, logTiming, startNanos, method, path, () -> {
                if (!ctx.channel().isActive()) {
                    return;
                }
                if (error != null) {
                    writeDispatchError(ctx, request, error);
                } else {
                    try {
                        writeDispatchResult(ctx, request, result);
                    } catch (Exception ex) {
                        writeDispatchError(ctx, request, ex);
                    }
                }
            });
        });
        // 响应超时并不代表存储操作结束，单独持有业务预算直到源 stage 完成。
        AtomicBoolean businessReleased = new AtomicBoolean();
        Runnable releaseBusiness = () -> {
            if (businessReleased.compareAndSet(false, true)) {
                try {
                    admission.completeBusiness();
                } catch (RuntimeException error) {
                    log.warn("释放 HTTP 异步业务资源失败", error);
                }
            }
        };
        try {
            stage.whenComplete((result, error) -> {
                try {
                    if (error == null) {
                        gate.complete(result);
                    } else {
                        gate.completeExceptionally(error);
                    }
                } finally {
                    releaseBusiness.run();
                }
            });
        } catch (RuntimeException error) {
            releaseBusiness.run();
            gate.completeExceptionally(error);
        }
    }

    private static void writeDispatchError(ChannelHandlerContext ctx, FullHttpRequest request, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null
                && (cause instanceof java.util.concurrent.CompletionException
                || cause instanceof java.util.concurrent.ExecutionException)) {
            cause = cause.getCause();
        }
        if (cause instanceof TimeoutException) {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.GATEWAY_TIMEOUT,
                    HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "HTTP operation timed out"));
            return;
        }
        if (cause instanceof HttpPipelineException e) {
            HttpUtil.writeJsonResponse(ctx, request, e.getStatus(), HttpResponseResult.fail(e.getCodeEnum(), e.getMessage()));
            return;
        }
        log.error("HTTP async dispatch error, uri={}", request.uri(), cause);
        HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR,
                HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, "Internal Server Error"));
    }

    private static void runOnChannelEventLoop(ChannelHandlerContext ctx, FullHttpRequest request, HttpContext httpContext, HttpRequestAdmission.Lease admission,
                                              boolean logTiming, long startNanos, String method, String path, Runnable writeOnEventLoop) {
        EventLoop eventLoop = ctx.channel().eventLoop();
        AtomicBoolean released = new AtomicBoolean();
        Runnable release = () -> {
            if (released.compareAndSet(false, true)) {
                try {
                    if (httpContext != null) {
                        httpContext.releaseResources();
                    }
                } finally {
                    try {
                        request.release();
                    } finally {
                        admission.close();
                    }
                    logTimingLine(logTiming, startNanos, method, path);
                }
            }
        };
        try {
            eventLoop.execute(() -> {
                try {
                    if (ctx.channel().isActive()) {
                        writeOnEventLoop.run();
                    }
                } finally {
                    release.run();
                }
            });
        } catch (RejectedExecutionException rejected) {
            // isShutdown 检查无法覆盖检查后关闭的竞争，必须处理实际提交失败。
            release.run();
        }
    }

    private static void writeDispatchResult(ChannelHandlerContext ctx, FullHttpRequest request, Object result) throws Exception {
        if (result instanceof HttpRawResponse raw) {
            HttpUtil.writeRawResponse(ctx, request, raw);
        } else if (result instanceof HttpFileResponse file) {
            HttpUtil.writeFileResponse(ctx, request, file);
        } else {
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.OK, result);
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
                if (stopping) {
                    throw new RejectedExecutionException("HTTP service stopping");
                }
                if (httpBusinessExecutor == null) {
                    httpBusinessExecutor = new DefaultEventExecutorGroup(threads,
                            new BasicThreadFactory.Builder().namingPattern("http-business-%d").daemon(true).build(),
                            MessageConstant.HTTP_BUSINESS_MAX_PENDING_TASKS, RejectedExecutionHandlers.reject());
                }
            }
        }
        return httpBusinessExecutor;
    }

}
