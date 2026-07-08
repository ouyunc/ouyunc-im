package com.ouyunc.base.http.client;

/** 出站 HTTP 调用异常。 */
public class HttpClientException extends RuntimeException {

    private final int statusCode;
    private final boolean retryable;

    public HttpClientException(String message) {
        this(message, 0, true, null);
    }

    public HttpClientException(String message, int statusCode, boolean retryable) {
        this(message, statusCode, retryable, null);
    }

    public HttpClientException(String message, int statusCode, boolean retryable, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
        this.retryable = retryable;
    }

    public int statusCode() {
        return statusCode;
    }

    public boolean retryable() {
        return retryable;
    }
}
