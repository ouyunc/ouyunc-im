package com.ouyunc.message.http.annotation;

import com.ouyunc.message.http.HttpRequestProcessor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 标在旧版 {@link HttpRequestProcessor} 实现类上：将请求体按 JSON 反序列化为指定类型。
 * 控制器风格请使用 {@link RequestBody} 标注方法参数。
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface HttpJsonBody {
    /**
     * JSON 对应的 Java 类型
     */
    Class<?> value();
}
