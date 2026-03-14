package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;


/**
 * @Author fangzhenxun
 * @Description 统一返回体
 * @Date 2020/4/9 10:28
 **/
public class HttpResponseResult<T> implements Serializable {

    private static final long serialVersionUID = 2047703336323178317L;
    /**
     * 返回码
     **/
    private int code;

    /**
     * 错误信息
     **/
    private String message;

    /**
     * 返回体信息
     **/
    private T data;

    /**
     * 是否成功，成功-程序按照可预知的方向执行，失败-程序不可预知错误
     **/
    private boolean success;

    /**
     * 当前时间戳毫秒
     **/
    private long timestamp;

    public HttpResponseResult() {
    }

    public HttpResponseResult(int code, String message, T data, boolean success) {
        this.code = code;
        this.message = message;
        this.data = data;
        this.success = success;
        this.timestamp = LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli();
    }



    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public static<T> HttpResponseResult<T> common(int code, String message, T data, boolean success) {
        return new HttpResponseResult<T>(code, message, data, success);
    }


    public static<T> HttpResponseResult<T> success() {
        return new HttpResponseResult<>(HttpResponseCodeEnum.OK.code(), HttpResponseCodeEnum.OK.getDescription(), null, true);
    }

    public static<T> HttpResponseResult<T> success(T data) {
        return new HttpResponseResult<T>(HttpResponseCodeEnum.OK.code(), HttpResponseCodeEnum.OK.getDescription(), data, true);
    }

    public static<T> HttpResponseResult<T> success(HttpResponseCodeEnum codeEnum, T data) {
        return new HttpResponseResult<T>(codeEnum.code(), codeEnum.getDescription(), data, true);
    }

    public static<T> HttpResponseResult<T> fail() {
        return new HttpResponseResult<>(HttpResponseCodeEnum.BAD_REQUEST.code(), HttpResponseCodeEnum.BAD_REQUEST.getDescription(), null, false);
    }

    public static<T> HttpResponseResult<T> fail(String message) {
        return new HttpResponseResult<>(HttpResponseCodeEnum.BAD_REQUEST.code(), message, null, false);
    }

    public static<T> HttpResponseResult<T> fail(HttpResponseCodeEnum codeEnum) {
        return new HttpResponseResult<>(codeEnum.code(), codeEnum.getDescription(), null, false);
    }

    public static<T> HttpResponseResult<T> fail(HttpResponseCodeEnum codeEnum, String message) {
        return new HttpResponseResult<>(codeEnum.code(), message, null, false);
    }

    public static<T> HttpResponseResult<T> error() {
        return new HttpResponseResult<>(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR.code(), HttpResponseCodeEnum.INTERNAL_SERVER_ERROR.getDescription(), null, false);
    }

    public static<T> HttpResponseResult<T> error(String message) {
        return new HttpResponseResult<>(HttpResponseCodeEnum.INTERNAL_SERVER_ERROR.code(), message, null, false);
    }

    public static<T> HttpResponseResult<T> error(HttpResponseCodeEnum codeEnum) {
        return new HttpResponseResult<>(codeEnum.code(), codeEnum.getDescription(), null, false);
    }

    public static<T> HttpResponseResult<T> error(HttpResponseCodeEnum codeEnum, String message) {
        return new HttpResponseResult<>(codeEnum.code(), message, null, false);
    }
}
