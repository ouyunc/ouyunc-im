package com.ouyunc.message.http;

import com.ouyunc.message.http.annotation.HttpJsonBody;
import com.ouyunc.message.http.annotation.IgnoreAuth;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.http.annotation.RequestPart;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 描述单条路由在 {@link HttpRequestPipeline} 中所需的前置行为（鉴权、body 类型等）。
 */
public final class HttpRouteDescriptor {

    private final Class<?> declaringClass;

    private final Method handlerMethod;

    private final Class<?> requestBodyClass;

    private final boolean ignoreAuth;

    private final boolean multipart;

    private HttpRouteDescriptor(Class<?> declaringClass, Method handlerMethod, Class<?> requestBodyClass, boolean ignoreAuth,
                                boolean multipart) {
        this.declaringClass = declaringClass;
        this.handlerMethod = handlerMethod;
        this.requestBodyClass = requestBodyClass;
        this.ignoreAuth = ignoreAuth;
        this.multipart = multipart;
    }

    /**
     * 旧版：类实现 {@link HttpRequestProcessor}，类上 {@link HttpJsonBody}、{@link IgnoreAuth}。
     */
    public static HttpRouteDescriptor forLegacy(HttpRequestProcessor<?> processor) {
        Class<?> c = processor.getClass();
        Class<?> bodyType = c.isAnnotationPresent(HttpJsonBody.class) ? c.getAnnotation(HttpJsonBody.class).value() : null;
        boolean ignoreAuth = c.isAnnotationPresent(IgnoreAuth.class);
        return new HttpRouteDescriptor(c, null, bodyType, ignoreAuth, false);
    }

    /**
     * 控制器方法：{@link RequestBody} 决定 JSON body 类型（可与 {@link com.ouyunc.message.http.annotation.RequestParam}、
     * {@link com.ouyunc.message.http.annotation.PathVariable}、{@link com.ouyunc.message.http.annotation.RequestHeader} 混用）；
     * {@link IgnoreAuth} 可在方法或类上（任一为 true 则跳过鉴权）。
     */
    public static HttpRouteDescriptor forControllerMethod(Class<?> controllerClass, Method method) {
        Class<?> bodyType = null;
        int bodyCount = 0;
        int partCount = 0;
        for (Parameter p : method.getParameters()) {
            if (p.isAnnotationPresent(RequestBody.class)) {
                bodyCount++;
                bodyType = p.getType();
            }
            if (p.isAnnotationPresent(RequestPart.class)) {
                partCount++;
            }
        }
        if (bodyCount > 1) {
            throw new IllegalStateException("至多一个 @RequestBody: " + method);
        }
        if (partCount > 0 && bodyType != null) {
            throw new IllegalStateException("不可同时使用 @RequestBody 与 @RequestPart: " + method);
        }
        boolean ignoreAuth = method.isAnnotationPresent(IgnoreAuth.class)
                || controllerClass.isAnnotationPresent(IgnoreAuth.class);
        return new HttpRouteDescriptor(controllerClass, method, bodyType, ignoreAuth, partCount > 0);
    }

    public Class<?> getDeclaringClass() {
        return declaringClass;
    }

    public Method getHandlerMethod() {
        return handlerMethod;
    }

    public Class<?> getRequestBodyClass() {
        return requestBodyClass;
    }

    public boolean isIgnoreAuth() {
        return ignoreAuth;
    }

    public boolean isMultipart() {
        return multipart;
    }
}
