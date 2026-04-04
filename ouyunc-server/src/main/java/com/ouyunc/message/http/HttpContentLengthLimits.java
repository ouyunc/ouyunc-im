package com.ouyunc.message.http;

import com.ouyunc.base.constant.HttpRequestConstant;
import com.ouyunc.message.context.MessageServerContext;

/**
 * HTTP 请求体长度：普通 JSON/文本 与 multipart 上传可分别限制；
 * {@link #aggregatorMaxBytes()} 为 Netty {@link io.netty.handler.codec.http.HttpObjectAggregator} 的实际上限（取二者较大值）。
 */
public final class HttpContentLengthLimits {

    public static final int DEFAULT_MAX_CONTENT_LENGTH = HttpRequestConstant.DEFAULT_MAX_HTTP_CONTENT_LENGTH;

    private HttpContentLengthLimits() {
    }

    /**
     * 普通请求体（JSON、{@code application/x-www-form-urlencoded} 等，非 multipart）业务层最大字节数。
     */
    public static int maxBytes() {
        int n = MessageServerContext.serverProperties().getHttpMaxContentLength();
        return n > 0 ? n : DEFAULT_MAX_CONTENT_LENGTH;
    }

    /**
     * {@code multipart/form-data} 业务层最大字节数；配置 ≤0 时与 {@link #maxBytes()} 相同。
     */
    public static int multipartMaxBytes() {
        int mp = MessageServerContext.serverProperties().getHttpMultipartMaxContentLength();
        if (mp > 0) {
            return mp;
        }
        return maxBytes();
    }

    /**
     * 聚合器与物理读入上限：须 ≥ {@link #maxBytes()} 且 ≥ {@link #multipartMaxBytes()}。
     */
    public static int aggregatorMaxBytes() {
        return Math.max(maxBytes(), multipartMaxBytes());
    }
}
