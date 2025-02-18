package com.ouyunc.base.exception;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;

/**
 * @Author fzx
 * @Description 自定义Message 异常
 **/
public class MessageException extends RuntimeException{

    // 定义错误码，使用枚举类型
    private final ExceptionCodeEnum errorCode;

    public MessageException(ExceptionCodeEnum errorCode) {
        this.errorCode = errorCode;
    }

    public MessageException(ExceptionCodeEnum errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    public MessageException(ExceptionCodeEnum errorCode, Throwable cause) {
        super(cause);
        this.errorCode = errorCode;
    }

    public MessageException(ExceptionCodeEnum errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    public ExceptionCodeEnum getErrorCode() {
        return errorCode;
    }
}
