package com.ouyunc.base.constant.enums;

import com.ouyunc.base.model.Protocol;

/**
 * 类型
 */
public interface Type<T extends Number> extends Protocol {

    /**
     * 获取类型值
     */
    T getType();
}
