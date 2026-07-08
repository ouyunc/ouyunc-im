package com.ouyunc.core.processor;

/**
 * @Author fzx
 * @Description: 处理器接口
 **/
@FunctionalInterface
public interface BiProcessor<R,T> {

    /**
     * @Author fzx
     * @Description 核心业务逻辑处理
     */
    void process(R r, T t);

}
