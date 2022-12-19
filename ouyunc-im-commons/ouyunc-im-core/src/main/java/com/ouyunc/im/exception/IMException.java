package com.ouyunc.im.exception;

/**
 * @Author fangzhenxun
 * @Description 自定义IM 异常
 * @Version V3.0
 **/
public class IMException extends RuntimeException{
    public IMException() {
    }

    public IMException(String message) {
        super(message);
    }
}
