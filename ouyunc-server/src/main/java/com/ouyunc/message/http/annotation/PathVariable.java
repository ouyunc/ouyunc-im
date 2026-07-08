package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 绑定路径模板中的变量（类似 Spring {@code @PathVariable}），路径需含 {@code {name}} 段，如 {@code /api/user/{id}}。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface PathVariable {

    /**
     * 模板中的变量名，须与 {@code {name}} 一致；为空时尝试使用编译参数名（需 {@code -parameters}）。
     */
    String value() default "";

    /**
     * 是否必须能解析到该路径段（通常为 true）。
     */
    boolean required() default true;
}
