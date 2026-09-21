package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * qos 级别枚举
 */
public enum QosLevelEnum {

    QOS_0(NumberConstant.NUMBER_0, "QOS_0", "至多一次"),
    QOS_1(NumberConstant.NUMBER_1, "QOS_1", "至少一次"),
    QOS_2(NumberConstant.NUMBER_2, "QOS_2", "仅一次"),
    QOS_3(NumberConstant.NUMBER_3, "QOS_3", "错误qos级别")

    ;


    private int level;

    private String name;

    private String description;

    QosLevelEnum(int level, String name, String description) {
        this.level = level;
        this.name = name;
        this.description = description;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public static QosLevelEnum getQosLevelEnum(int level) {
        for (QosLevelEnum qosLevelEnum : QosLevelEnum.values()) {
            if (qosLevelEnum.level == level) {
                return qosLevelEnum;
            }
        }
        return null;
    }
}
