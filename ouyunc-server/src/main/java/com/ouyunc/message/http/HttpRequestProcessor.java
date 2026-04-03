package com.ouyunc.message.http;

/**
 * HTTP 请求处理：单入口 {@link #process(HttpContext)}。
 * <p>
 * 既可以是类实现（类上路由注解），也可以是控制器方法对应的 lambda（由 {@link HttpRouteRegistry} 注册）。
 */
@FunctionalInterface
public interface HttpRequestProcessor<R> {

    /**
     * @param httpContext 含 channel、FullHttpRequest、分发器写入的 body、appKey 等
     */
    R process(HttpContext httpContext) throws Exception;
}
