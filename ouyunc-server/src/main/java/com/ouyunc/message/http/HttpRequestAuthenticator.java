package com.ouyunc.message.http;

/**
 * HTTP 请求鉴权（业务可自定义：仅 appKey、Token、签名等）。通过 {@link HttpAuthenticators#setGlobal(HttpRequestAuthenticator)} 全局注册一份实现即可，
 * 所有未标注 {@link com.ouyunc.message.http.annotation.IgnoreAuth} 的路由共用。
 * <p>
 * 调用时机：在 {@link HttpRequestPipeline} 中、{@link HttpContext} 已包含 channel、request、rawBody、pathVariables 之后，
 * 在按 {@link com.ouyunc.message.http.annotation.RequestBody} 反序列化 JSON 之前执行（便于从 Header 或自行解析 rawBody 取 token）。
 * <p>
 * 失败请抛出 {@link HttpPipelineException} 以返回对应 HTTP 状态与业务码。
 */
public interface HttpRequestAuthenticator {

    /**
     * 执行鉴权；成功时可将 principal、appKey 等写入 {@link HttpContext}（如 {@link HttpContext#setAppKey(String)}）。
     */
    void authenticate(HttpContext httpContext) throws HttpPipelineException;
}
