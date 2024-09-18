package com.ouyunc.core.generator;

/**
 * 全局唯一 id生成器
 */
public interface IdGenerator<T> {


    /**
     * id生成器接口
     */
    T generateId();

}
