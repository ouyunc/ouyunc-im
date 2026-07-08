package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * HTTP 前置处理（鉴权、body 解析）失败时抛出，由 {@link HttpRequestDispatcher} 转为 JSON 响应。
 */
public final class HttpPipelineException extends Exception {

    private final HttpResponseStatus status;
    private final HttpResponseCodeEnum codeEnum;

    public HttpPipelineException(HttpResponseStatus status, HttpResponseCodeEnum codeEnum, String message) {
        super(message);
        this.status = status;
        this.codeEnum = codeEnum;
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public HttpResponseCodeEnum getCodeEnum() {
        return codeEnum;
    }
}
