package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * 消息投递重试开关。发送方受理结果独立返回，不受此值控制。
 * 0 不登记 QoS 幂等占位或下行重试；1 开启幂等占位和下行重试。
 */
public enum QosLevelEnum {

    QOS_0(NumberConstant.NUMBER_0),
    QOS_1(NumberConstant.NUMBER_1)

    ;


    private final int level;

    QosLevelEnum(int level) {
        this.level = level;
    }

    public int getLevel() {
        return level;
    }

    /** 入站只接受已经实现且语义明确的级别。 */
    public static boolean isSupportedLevel(int level) {
        return level == QOS_0.level || level == QOS_1.level;
    }
}
