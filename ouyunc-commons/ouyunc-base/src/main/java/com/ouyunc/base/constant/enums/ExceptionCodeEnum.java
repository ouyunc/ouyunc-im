package com.ouyunc.base.constant.enums;

/**
 * 异常码枚举 todo 待完善
 */
public enum ExceptionCodeEnum {


    UNKNOWN_ERROR(10001, "未知错误"),



    PERSISTENCE_ERROR(20001, "持久化错误"),


    CACHE_PERSISTENCE_ERROR(20002, "缓存持久化错误"),


    ONE_2_ONE_SEND_ERROR(30001, "持久化错误"),

    WITHDRAW_MESSAGE_ERROR(40001, "撤回消息错误"),


    READ_RECEIPT_MESSAGE_ERROR(50001, "读已回执消息错误"),

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
