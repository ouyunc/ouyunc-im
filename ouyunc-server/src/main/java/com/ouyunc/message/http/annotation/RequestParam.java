package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 绑定 URI query 参数（类似 Spring {@code @RequestParam}）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestParam {

    /**
     * 参数名；为空时尝试使用编译参数名（需 {@code -parameters}）。
     */
    String value() default "";

    /**
     * 是否必须有该参数（为 true 且未提供、且无 {@link #defaultValue} 时视为非法请求）。
     */
    boolean required() default true;

    /**
     * 缺省时的默认值；非空则表示未传参时使用该字符串再参与类型转换。
     */
    String defaultValue() default "";
}
