package com.ouyunc.base.exception;

/**
 * @Author fzx
 * @Description 自定义Message 异常
 **/
public class MessageException extends RuntimeException{
    public MessageException() {
    }

    public MessageException(String message) {
        super(message);
    }

    public MessageException(Throwable cause) {
        super(cause);
    }
}
