package com.ouyunc.message.processor.http.demo;

import java.io.Serial;
import java.io.Serializable;

/**
 * 演示 {@code POST + application/json + @RequestBody} 的请求体类型。
 */
public class HttpDemoJsonRequest implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private String message;

    private Integer repeat;

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public Integer getRepeat() {
        return repeat;
    }

    public void setRepeat(Integer repeat) {
        this.repeat = repeat;
    }
}
