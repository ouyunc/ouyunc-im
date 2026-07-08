package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 控制器类上的路径前缀（类似 Spring {@code @RequestMapping}），与方法上映射路径拼接为完整路径。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpRequestMapping {
    /**
     * 前缀路径，如 {@code /api/im}，可与 {@link PostHttpRequest#value()} 等拼接。
     */
    String value() default "";
}
