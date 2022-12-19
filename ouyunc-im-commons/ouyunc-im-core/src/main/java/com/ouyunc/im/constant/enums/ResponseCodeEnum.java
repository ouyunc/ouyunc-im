package com.ouyunc.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description 返回体code码枚举类型
 * @Date 2020/4/9 10:48
 **/
public enum ResponseCodeEnum {

    OK(200, "成功"),
    BAD_REQUEST(400, "错误请求"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "权限不足（未登录）"),
    NOT_FOUND(404, "服务找不到"),
    METHOD_NOT_ALLOWED(405, "方法不允许"),
    NOT_ACCEPTABLE(406, "请求不接受"),
    UNSUPPORTED_MEDIA_TYPE(415, "不支持媒体类型"),
    INTERNAL_SERVER_ERROR(500, "服务器内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用");


    private int code;

    private String description;


    ResponseCodeEnum(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int code() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }


}
