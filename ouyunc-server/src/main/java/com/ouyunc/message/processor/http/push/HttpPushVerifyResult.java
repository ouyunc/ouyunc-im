package com.ouyunc.message.processor.http.push;

/**
 * HTTP 推送业务校验结果。
 */
public final class HttpPushVerifyResult {

    public enum Outcome {
        PASS,
        REJECT,
        ERROR
    }

    private final Outcome outcome;
    private final String message;

    private HttpPushVerifyResult(Outcome outcome, String message) {
        this.outcome = outcome;
        this.message = message;
    }

    public static HttpPushVerifyResult pass() {
        return new HttpPushVerifyResult(Outcome.PASS, null);
    }

    public static HttpPushVerifyResult reject(String message) {
        return new HttpPushVerifyResult(Outcome.REJECT, message);
    }

    public static HttpPushVerifyResult error(String message) {
        return new HttpPushVerifyResult(Outcome.ERROR, message);
    }

    public Outcome getOutcome() {
        return outcome;
    }

    public String getMessage() {
        return message;
    }

    public boolean isPass() {
        return outcome == Outcome.PASS;
    }

    public boolean isReject() {
        return outcome == Outcome.REJECT;
    }

    public boolean isError() {
        return outcome == Outcome.ERROR;
    }
}
