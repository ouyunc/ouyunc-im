package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;

/**
 * @Author fzx
 * @Description: 基础抽象处理类
 **/
public abstract class AbstractBaseProcessor<T extends Number> implements Processor<Packet> {
    /**
     * 类型
     */
    public abstract Type<? extends T> type();


}
