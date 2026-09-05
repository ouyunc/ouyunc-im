package com.ouyunc.base.constant;

public class HttpRequestConstant extends HttpConstant{
    /**
     * 默认 HTTP 聚合请求体最大字节（与消息服务 {@code ouyunc.message.http.max-content-length} 默认一致）。
     */
    public static final int DEFAULT_MAX_HTTP_CONTENT_LENGTH = 1048576;

    /**
     * LB 存活探活（进程在即 200，无需鉴权）
     */
    public static final String HTTP_HEALTH_PATH = "/health";

    /**
     * LB 就绪探活（摘流或不健康时 HTTP 503，无需鉴权）
     */
    public static final String HTTP_READY_PATH = "/ready";

    /**
     * 运维摘流：拒绝新登录并使 /ready 返回 503（需 X-App-Key）
     */
    public static final String HTTP_ADMIN_DRAIN_PATH = "/admin/drain";

    /**
     * 运维取消摘流（需 X-App-Key）
     */
    public static final String HTTP_ADMIN_UNDRAIN_PATH = "/admin/undrain";

    /**
     * 运维通知本机在线客户端主动断开并重连（需 X-App-Key）；会先进入摘流，服务端不主动 close
     */
    public static final String HTTP_ADMIN_KICK_CLIENTS_PATH = "/admin/kick-clients";

    /**
     * 请求头：应用 appKey（HTTP 推送等接口必填，不再从 JSON body 读取）
     */
    public static final String HTTP_HEADER_APP_KEY = "X-App-Key";

    /**
     * 请求头：调用方链路追踪 ID，服务端写入 MDC（可在 logback 使用 %X{requestId}）
     */
    public static final String HTTP_HEADER_REQUEST_ID = "X-Request-Id";

    /**
     * 请求头：Bearer JWT，HTTP 推送开启 JWT 鉴权时用于解析发送方身份与权限。
     */
    public static final String HTTP_HEADER_AUTHORIZATION = "Authorization";

}
