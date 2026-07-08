package com.ouyunc.message.http.annotation;

import com.ouyunc.message.http.HttpRequestProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在 {@link HttpRequestProcessor} 实现类或 {@link HttpRestController} 方法/类上：跳过 {@link com.ouyunc.message.http.HttpRequestAuthenticator}。未标注时执行鉴权。
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
public @interface IgnoreAuth {
}
