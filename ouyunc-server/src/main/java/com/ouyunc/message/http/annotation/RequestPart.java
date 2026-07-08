package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 绑定 {@code multipart/form-data} 中的表单项或文件域（名称与 {@code Content-Disposition} 的 {@code name=} 一致）。
 * <p>
 * 与 {@link RequestBody} 互斥；需与 {@link com.ouyunc.message.http.HttpRouteDescriptor#isMultipart()} 路由一起使用。
 * 参数名：优先 {@link #value()}，否则在编译带 {@code -parameters} 时用形参名。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestPart {

    String value() default "";

    boolean required() default true;
}
