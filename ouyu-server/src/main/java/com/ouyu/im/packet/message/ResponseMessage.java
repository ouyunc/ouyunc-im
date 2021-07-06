package com.ouyu.im.packet.message;

import io.protostuff.Tag;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * @Author fangzhenxun
 * @Description: 返回客户端的信息
 * @Version V1.0
 **/
@Deprecated
public class ResponseMessage<T> implements Serializable {
    /**
     * 返回体code
     **/
    @Tag(1)
    private int code;

    /**
     * 返回体信息
     **/
    @Tag(2)
    private T data;

    /**
     * 时间戳
     **/
    @Tag(3)
    private long timestamp;

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }

    public ResponseMessage() {
    }

    public ResponseMessage(int code, T data, long timestamp) {
        this.code = code;
        this.data = data;
        this.timestamp = timestamp;
    }

    public static ResponseMessage success(Object data) {
        return new ResponseMessage<>(200, data, LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli());
    }

    public static ResponseMessage fail() {
        return new ResponseMessage<>(500, "登录失败！", LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli());
    }


    public static ResponseMessage fail(String errorMsg) {
        return new ResponseMessage<>(500, errorMsg, LocalDateTime.now().toInstant(ZoneOffset.of("+8")).toEpochMilli());
    }
}
