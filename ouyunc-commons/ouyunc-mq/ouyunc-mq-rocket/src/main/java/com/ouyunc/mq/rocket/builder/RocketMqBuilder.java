package com.ouyunc.mq.rocket.builder;

/**
 * @author fzx
 * @version 1.0
 * @description RocketMQ 抽象创建者接口
 */
@FunctionalInterface
public interface RocketMqBuilder<T> {

    /**
     * 构建 RocketMQ 相关组件
     *
     * @return 构建结果
     */
    T build();
}
