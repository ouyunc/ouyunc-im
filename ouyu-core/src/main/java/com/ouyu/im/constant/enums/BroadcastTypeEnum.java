package com.ouyu.im.constant.enums;

/**
 * @Author fangzhenxun
 * @Description: 广播类型枚举
 * @Version V1.0
 **/
public enum BroadcastTypeEnum {

    ONLINE(1,"online", "上线"),
    OFFLINE(2,"offline", "离线");

    private int code;
    private String type;
    private String description;

    BroadcastTypeEnum(int code, String type, String description) {
        this.code = code;
        this.type = type;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static BroadcastTypeEnum getBroadcastTypeEnumByType(String type) {
        for (BroadcastTypeEnum broadcastTypeEnum : BroadcastTypeEnum.values()) {
            if (broadcastTypeEnum.type.equals(type)) {
                return broadcastTypeEnum;
            }
        }
        return null;
    }
}
