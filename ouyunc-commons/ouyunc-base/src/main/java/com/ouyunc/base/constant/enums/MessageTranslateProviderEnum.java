package com.ouyunc.base.constant.enums;

import com.ouyunc.base.constant.NumberConstant;

/**
 * 译文来源。落库 tinyint；缓存命中仍回写当初的 MT/LLM/HUMAN，不用 cache 当 provider。
 */
public enum MessageTranslateProviderEnum implements Type<Byte> {

    /** 机器翻译，自动入站默认 */
    MT(NumberConstant.NUMBER_1, "机器翻译"),
    /** 大模型 */
    LLM(NumberConstant.NUMBER_2, "大模型"),
    /** 人工修正，允许覆盖已有译文 */
    HUMAN(NumberConstant.NUMBER_3, "人工修正");

    private final byte code;
    private final String desc;

    MessageTranslateProviderEnum(byte code, String desc) {
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

    public static MessageTranslateProviderEnum of(Byte code) {
        if (code == null) {
            return null;
        }
        for (MessageTranslateProviderEnum item : values()) {
            if (item.code == code) {
                return item;
            }
        }
        return null;
    }
}
