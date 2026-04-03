package com.ouyunc.message.http.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标记 HTTP 控制器类（类似 Spring {@code @RestController}），与类上 {@link HttpRequestMapping}、方法上
 * {@link GetHttpRequest}/{@link PostHttpRequest}/{@link HttpRequest} 组合使用。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpRestController {
}
