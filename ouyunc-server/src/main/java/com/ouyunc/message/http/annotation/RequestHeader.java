package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 绑定请求头到方法参数（类似 Spring {@code @RequestHeader}）。
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.RUNTIME)
public @interface RequestHeader {
    /**
     * 头名称，如 {@link com.ouyunc.base.constant.HttpRequestConstant#HTTP_HEADER_APP_KEY}
     */
    String value();
}
