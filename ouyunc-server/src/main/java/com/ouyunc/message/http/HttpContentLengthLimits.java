package com.ouyunc.message.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.message.context.MessageServerContext;

/**
 * HTTP 请求体长度上限（与 {@link io.netty.handler.codec.http.HttpObjectAggregator}、业务层校验一致）。
 */
public final class HttpContentLengthLimits {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = HttpRequestConstant.DEFAULT_MAX_HTTP_CONTENT_LENGTH;

    private HttpContentLengthLimits() {
    }

    public static int maxBytes() {
        int n = MessageServerContext.serverProperties().getHttpMaxContentLength();
        return n > 0 ? n : DEFAULT_MAX_CONTENT_LENGTH;
    }
}
