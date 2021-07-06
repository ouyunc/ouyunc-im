package com.ouyu.im.exception;

/**
 * @Author fangzhenxun
 * @Description 自定义IM 异常
 **/
public class IMException extends RuntimeException{
    public IMException() {
    }

    public IMException(String message) {
        super(message);
    }
}
