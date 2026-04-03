package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * HTTP 路由：方法 + 路径
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpRequest {
    /**
     * HTTP 方法: GET,POST,DELETE,PUT ...
     */
    String method();

    /**
     * 路径
     */
    String path();
}
