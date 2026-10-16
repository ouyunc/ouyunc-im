package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import io.netty.handler.codec.http.HttpResponseStatus;

/**
 * HTTP 前置处理（鉴权、body 解析）失败时抛出，由 {@link HttpRequestDispatcher} 转为 JSON 响应。
 */
public final class HttpPipelineException extends Exception {

    private final HttpResponseStatus status;
    private final HttpResponseCodeEnum codeEnum;
    /** 逐消息业务码，例如内容审核繁忙 40012。空则由受理层按状态填默认码。 */
    private final Integer businessCode;

    public HttpPipelineException(HttpResponseStatus status, HttpResponseCodeEnum codeEnum, String message) {
        this(status, codeEnum, message, null);
    }

    public HttpPipelineException(HttpResponseStatus status, HttpResponseCodeEnum codeEnum, String message,
                                 Integer businessCode) {
        super(message);
        this.status = status;
        this.codeEnum = codeEnum;
        this.businessCode = businessCode;
    }

    public HttpResponseStatus getStatus() {
        return status;
    }

    public HttpResponseCodeEnum getCodeEnum() {
        return codeEnum;
    }

    /**
     * @return 逐消息业务码，未指定时为 null
     */
    public Integer getBusinessCode() {
        return businessCode;
    }
}
