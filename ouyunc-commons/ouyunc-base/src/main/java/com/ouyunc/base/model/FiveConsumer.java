package com.ouyunc.base.model;

/**
 * 多元参数消费者
 * @param <A>
 * @param <B>
 * @param <C>
 * @param <D>
 * @param <E>
 */
@FunctionalInterface
public interface FiveConsumer<A, B, C, D, E> {
    void accept(A a, B b, C c, D d, E e);
}