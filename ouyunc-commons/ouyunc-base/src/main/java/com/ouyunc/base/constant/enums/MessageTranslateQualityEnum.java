package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * 翻译质量档：自动入站用 FAST；手动可升 QUALITY。
 */
public enum MessageTranslateQualityEnum implements Type<Byte> {

    FAST(NumberConstant.NUMBER_1, "快速（机器翻译优先）"),
    QUALITY(NumberConstant.NUMBER_2, "高质量（大模型）");

    private final byte code;
    private final String desc;

    MessageTranslateQualityEnum(byte code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    @Override
    public Byte getType() {
        return code;
    }

    public String getDesc() {
        return desc;
    }

    public static MessageTranslateQualityEnum of(Byte code) {
        if (code == null) {
            return FAST;
        }
        for (MessageTranslateQualityEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return FAST;
    }
}
