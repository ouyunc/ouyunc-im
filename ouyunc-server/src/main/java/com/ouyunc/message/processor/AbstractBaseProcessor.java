package com.ouyunc.message.processor;

import com.ouyunc.base.constant.enums.Type;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.repository.DefaultRepository;
import com.ouyunc.repository.Repository;

/**
 * @Author fzx
 * @Description: 基础抽象处理类
 **/
public abstract class AbstractBaseProcessor<T extends Number> implements Processor<Packet> {
    /**
     * 类型
     */
    public abstract Type<? extends T> type();

    /**
     * 获取数据存储实现类, 子类可以重写来实现自定义存储实现
     */
    @SuppressWarnings("unchecked")
    public <R extends Repository> R repository() {
        return (R) new DefaultRepository();
    }
}
