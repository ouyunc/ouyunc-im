package com.ouyunc.im.packet.message;

import java.io.Serializable;

/**
 * 扩展消息 message, 额外字段数据，额外消息
 */
public class ExtraMessage implements Serializable {
    /**
     * 外部额外扩展数据，临时存放
     */
    private String outExtraData;

    /**
     * 内部额外扩展数据，临时存放
     */
    private InnerExtraData innerExtraData;


    public String getOutExtraData() {
        return outExtraData;
    }

    public void setOutExtraData(String outExtraData) {
        this.outExtraData = outExtraData;
    }

    public InnerExtraData getInnerExtraData() {
        return innerExtraData;
    }

    public void setInnerExtraData(InnerExtraData innerExtraData) {
        this.innerExtraData = innerExtraData;
    }
}
