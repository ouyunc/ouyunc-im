
package com.ouyunc.base.model;

import java.lang.annotation.*;
import java.lang.annotation.Target;

/***
 * @author fzx
 * @description 排序注解，值越小优先级越高。可用于拦截器、{@code MessageListener} 实现类等按序加载的场景。
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD, ElementType.FIELD})
@Documented
public @interface Order {
    int value() default Integer.MAX_VALUE;
}
