package com.ouyunc.message.http.annotation;

import com.ouyunc.base.constant.HttpConstant;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * GET 映射，可用于类（单 handler）或方法（控制器方法）
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@HttpRequest(method = HttpConstant.GET, path = "")
public @interface GetHttpRequest {
    String value();
}
