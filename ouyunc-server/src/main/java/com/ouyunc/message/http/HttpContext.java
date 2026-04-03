package com.ouyunc.message.http;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;

import java.util.Collections;
import java.util.Map;

/**
 * HTTP 接口层上下文：包含 Netty 上下文与原始请求，以及分发器填充的 rawBody、反序列化 body、鉴权得到的 appKey、路径变量等。
 */
public final class HttpContext {

    private final ChannelHandlerContext channelContext;

    private final FullHttpRequest request;

    private final String rawBody;

    private Object body;

    private String appKey;

    private Map<String, String> pathVariables = Collections.emptyMap();

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
}
