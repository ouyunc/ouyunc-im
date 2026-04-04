package com.ouyunc.base.constant;

public class HttpRequestConstant extends HttpConstant{
    /**
     * 默认 HTTP 聚合请求体最大字节（与消息服务 {@code ouyunc.message.http.max-content-length} 默认一致）。
     */
    public static final int DEFAULT_MAX_HTTP_CONTENT_LENGTH = 1048576;

    /**
     * HTTP 推送接口路径
     */
    public static final String HTTP_PUSH_API_PATH = "/api/im/push";

    /**
     * 请求头：应用 appKey（HTTP 推送等接口必填，不再从 JSON body 读取）
     */
    public static final String HTTP_HEADER_APP_KEY = "X-App-Key";

    /**
     * 请求头：调用方链路追踪 ID，服务端写入 MDC（可在 logback 使用 %X{requestId}）
     */
    public static final String HTTP_HEADER_REQUEST_ID = "X-Request-Id";

}
