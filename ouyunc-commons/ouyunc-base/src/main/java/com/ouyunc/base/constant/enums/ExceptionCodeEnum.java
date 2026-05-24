package com.ouyunc.base.constant.enums;

/**
 * 异常码枚举
 */
public enum ExceptionCodeEnum {


    UNKNOWN_ERROR(10000, "未知错误"),


    PERSISTENCE_ERROR(20001, "持久化错误"),
    CACHE_PERSISTENCE_ERROR(20002, "缓存持久化错误"),
    MQ_PERSISTENCE_ERROR(20003, "MQ持久化错误"),



    LOGIN_AUTH_ERROR(30001, "登录认证错误"),
    LOGIN_VERIFY_ERROR(30002, "登录校验错误"),


    WITHDRAW_MESSAGE_ERROR(40001, "撤回消息错误"),
    WITHDRAW_MESSAGE_VERIFY_ERROR(40002, "撤回消息校验错误"),
    MESSAGE_REF_INVALID_ERROR(40003, "引用消息无效或超过上限"),


    READ_RECEIPT_MESSAGE_ERROR(50001, "读已回执消息错误"),
    READ_RECEIPT_MESSAGE_VERIFY_ERROR(50002, "读已回执消息校验错误"),


    GROUP_MEMBER_NOT_EXIST_ERROR(60001, "群成员不存在"),
    GROUP_MEMBER_COUNT_MISMATCH_ERROR(60002, "群成员和群成员关系数量不匹配"),
    GROUP_AT_MENTION_INVALID_ERROR(60003, "群@成员无效或超过上限"),


    UN_BIND_ERROR(70001, "客户端解绑错误"),

    LOGIN_KEEP_ALIVE_ERROR(80001, "登录保活错误"),

    SERVER_SPLIT_BRAIN_ERROR(90001, "集群服务脑裂错误"),

    ILLEGAL_MESSAGE_TYPE_ERROR(100001, "非法消息类型"),
    ILLEGAL_DEVICE_TYPE_ERROR(100002, "非法设备类型"),

    ACQUIRE_LOCK_ERROR(200001, "获取锁异常"),
    UN_LOCK_ERROR(200002, "解锁异常"),

    BIND_FRIEND_ERROR(300001, "绑定好友异常"),

    SCHEDULE_TASK_ERROR(400001, "调度任务异常"),

    USER_NOT_EXIST(500001, "用户不存在"),
    GROUP_NOT_EXIST(500002, "群组不存在"),

    REQUEST_SESSION_NOT_EXIST(600001, "请求会话不存在"),
    REQUEST_SESSION_MISMATCH(600002, "请求会话不匹配"),
    REQUEST_SESSION_PROGRESS_MISMATCH(600003, "请求会话进度不匹配"),

    BIND_GROUP_ERROR(700001, "绑定群组异常"),


    SAVE_OFFLINE_MESSAGE_ERROR(800001, "保存离线消息异常"),


    MESSAGE_TYPE_ERROR(1000001, "消息类型异常"),
    MESSAGE_CONTENT_TYPE_ERROR(1000002, "消息内容类型异常"),


    ;

    private final int code;


    private final String message;

    ExceptionCodeEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public static ExceptionCodeEnum getByCode(int code) {
        for (ExceptionCodeEnum exceptionCodeEnum : ExceptionCodeEnum.values()) {
            if (exceptionCodeEnum.getCode() == code) {
                return exceptionCodeEnum;
            }
        }
        return null;
    }
}
