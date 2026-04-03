package com.ouyunc.base.constant;

public class HttpRequestConstant extends HttpConstant{
    /**
     * HTTP 推送接口路径
     */
    public static final String HTTP_PUSH_API_PATH = "/api/im/push";

    /**
     * 请求头：应用 appKey（与 JSON body 中 appKey 二选一或同时提供，解析时优先取 Header）
     */
    public static final String HTTP_HEADER_APP_KEY = "X-App-Key";

}
