package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
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

/**
 * HTTP 请求分发：按 method + path 查表调用 HttpRequestProcessor，404/异常时写 JSON，统一 release request。
 */
public class HttpRequestDispatcher {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestDispatcher.class);
    private static final String DEFAULT_HTTP_HANDLER_SCAN_PACKAGE = "com.ouyunc.message.processor";

    private static final HttpRequestDispatcher INSTANCE = new HttpRequestDispatcher();

    private final HttpRequestProcessorRegistrar registrar = new HttpRequestProcessorRegistrar();

    private HttpRequestDispatcher() {
        List<String> packages = null;
        if (MessageServerContext.serverProperties() != null) {
            packages = MessageServerContext.serverProperties().getHttpProcessorScanPackagePaths();
        }
        if (CollectionUtils.isEmpty(packages)) {
            packages = Collections.singletonList(DEFAULT_HTTP_HANDLER_SCAN_PACKAGE);
        }
        for (String basePackage : packages) {
            registrar.scanAndRegister(basePackage.trim());
        }
    }

    public static HttpRequestDispatcher getInstance() {
        return INSTANCE;
    }

    public void register(String method, String path, HttpRequestProcessor handler) {
        registrar.register(method, path, handler);
    }

    /**
     * 分发 FullHttpRequest：仅当 msg 为 FullHttpRequest 时调用
     */
    public void dispatch(ChannelHandlerContext ctx, FullHttpRequest request) {
        try {
            String path = HttpUtil.pathFromUri(request.uri());
            String method = request.method().name();
            HttpRequestProcessor processor = registrar.find(method, path);
            if (processor == null) {
                HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.NOT_FOUND, HttpResponseResult.fail(HttpResponseCodeEnum.NOT_FOUND));
                return;
            }
            processor.process(ctx, request);
        } catch (Exception e) {
            log.error("HTTP dispatch error, uri={}", request.uri(), e);
            HttpUtil.writeJsonResponse(ctx, request, HttpResponseStatus.INTERNAL_SERVER_ERROR, HttpResponseResult.error(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR, e.getMessage()));
        }
    }

}
