package com.ouyunc.core.listener;

import com.ouyunc.base.constant.enums.EventRingEnum;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface EventListener {

    /**
     * 执行顺序（核心）
     * - 相同 order 的监听器并行执行（handleEventsWith）
     * - 不同 order 的监听器按从小到大串行执行（.then()）
     */
    int order() default 100;

    /**
     * 使用哪个性能等级的 RingBuffer
     */
    EventRingEnum ring() default EventRingEnum.NORMAL;
}
