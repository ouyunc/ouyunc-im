package com.ouyunc.message.http;

/**
 * 已注册的 HTTP 路由：前置描述信息 + 处理器（同一 {@link HttpRequestProcessor} 抽象）。
 */
public final class HttpRegisteredRoute {

    private final HttpRouteDescriptor descriptor;

    private final HttpRequestProcessor<?> processor;

    public HttpRegisteredRoute(HttpRouteDescriptor descriptor, HttpRequestProcessor<?> processor) {
        this.descriptor = descriptor;
        this.processor = processor;
    }

    public HttpRouteDescriptor getDescriptor() {
        return descriptor;
    }

    public HttpRequestProcessor<?> getProcessor() {
        return processor;
    }
}
