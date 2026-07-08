package com.ouyunc.mq.kafka.builder;

/**
 * @Author fangzhenxun
 * @Description kafka 抽象创建者
 * @Date 2020/3/13 11:03
 **/
@FunctionalInterface
public interface KafkaMqBuilder<T> {


    /**
     * @Author fangzhenxun
     * @Description  kafka 的相关创建者
     * @Date 2020/3/13 11:04
     * @param
     * @return T
     **/
    T build();
}
