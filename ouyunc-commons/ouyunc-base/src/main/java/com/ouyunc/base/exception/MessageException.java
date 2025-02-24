package com.ouyunc.base.exception;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;

/**
 * @Author fzx
 * @Description 自定义Message 异常
 **/
public class MessageException extends RuntimeException{

    private ExceptionCodeEnum errorCode;

    private Object data;

    private long exceptionTime;


    public MessageException(ExceptionCodeEnum code) {
        super(code.getMessage());
        errorCode = code;
    }

    public MessageException(String message, ExceptionCodeEnum errorCode, Object data, long exceptionTime) {
        super(message);
        this.errorCode = errorCode;
        this.data = data;
        this.exceptionTime = exceptionTime;
    }
    public MessageException(ExceptionCodeEnum errorCode, Object data, long exceptionTime) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
        this.exceptionTime = exceptionTime;
    }

    public MessageException() {
    }

    public MessageException(String message) {
        super(message);
    }


    public MessageException(Throwable ex) {
        super(ex);
    }


    public ExceptionCodeEnum getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(ExceptionCodeEnum errorCode) {
        this.errorCode = errorCode;
    }

    public Object getData() {
        return data;
    }

    public void setData(Object data) {
        this.data = data;
    }

    public long getExceptionTime() {
        return exceptionTime;
    }

    public void setExceptionTime(long exceptionTime) {
        this.exceptionTime = exceptionTime;
    }
}
