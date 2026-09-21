package com.ouyunc.message.http;

/**
 * HTTP 请求处理：单入口 {@link #process(HttpContext)}。
 * <p>
 * 既可以是类实现（类上路由注解），也可以是控制器方法对应的 lambda（由 {@link HttpRouteRegistry} 注册）。
 */
@FunctionalInterface
public interface HttpRequestProcessor<R> {

    /**
     * 返回 CompletionStage 的处理器必须先提取 DTO；异步任务不得持有原始 request/multipart，
     * 它们由分发器在响应完成、连接关闭或超时后释放。
     *
     * @param httpContext 含 channel、FullHttpRequest、分发器写入的 body、appKey 等
     */
    R process(HttpContext httpContext) throws Exception;
}
