package com.ouyunc.message.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP 接口层上下文：包含 Netty 上下文与原始请求，以及分发器填充的 rawBody、反序列化 body、鉴权得到的 appKey、路径变量等。
 * <p>
 * 若路由使用 {@code @RequestBody} 且 JSON 自字节数组解析，则 {@link #getRawBody()} 可能为空字符串（请使用 {@link #getBody()}）。
 */
public final class HttpContext {

    private final ChannelHandlerContext channelContext;

    private final FullHttpRequest request;

    private final String rawBody;

    private Object body;

    private String appKey;

    private Map<String, String> pathVariables = Collections.emptyMap();

    private HttpMultipartHolder multipart;

    /** {@code application/x-www-form-urlencoded} 解析结果，供 {@code @RequestParam} 与 query 合并查找 */
    private Map<String, String> formUrlEncodedParams = Collections.emptyMap();

    public HttpContext(ChannelHandlerContext channelContext, FullHttpRequest request, String rawBody) {
        this.channelContext = channelContext;
        this.request = request;
        this.rawBody = rawBody;
    }

    public ChannelHandlerContext getChannelContext() {
        return channelContext;
    }

    public FullHttpRequest getRequest() {
        return request;
    }

    public String getRawBody() {
        return rawBody;
    }

    @SuppressWarnings("unchecked")
    public <T> T getBody() {
        return (T) body;
    }

    public void setBody(Object body) {
        this.body = body;
    }

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public void setPathVariables(Map<String, String> pathVariables) {
        this.pathVariables = pathVariables == null ? Collections.emptyMap() : Map.copyOf(pathVariables);
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }

    public String getPathVariable(String name) {
        return pathVariables.get(name);
    }

    public HttpMultipartHolder getMultipart() {
        return multipart;
    }

    public void setMultipart(HttpMultipartHolder multipart) {
        this.multipart = multipart;
    }

    /**
     * 释放 multipart 临时文件等资源；由分发器在单次请求结束时调用。
     */
    public void releaseResources() {
        if (multipart != null) {
            multipart.destroy();
            multipart = null;
        }
    }

    public Map<String, String> getFormUrlEncodedParams() {
        return formUrlEncodedParams;
    }

    public void setFormUrlEncodedParams(Map<String, String> formUrlEncodedParams) {
        this.formUrlEncodedParams = formUrlEncodedParams == null ? Collections.emptyMap() : Map.copyOf(formUrlEncodedParams);
    }
}
