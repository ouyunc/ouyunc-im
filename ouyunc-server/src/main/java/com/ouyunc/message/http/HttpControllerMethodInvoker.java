package com.ouyunc.message.http;

import com.ouyunc.base.constant.enums.HttpResponseCodeEnum;
import com.ouyunc.base.utils.HttpUtil;
import com.ouyunc.message.http.annotation.PathVariable;
import com.ouyunc.message.http.annotation.RequestBody;
import com.ouyunc.message.http.annotation.RequestHeader;
import com.ouyunc.message.http.annotation.RequestParam;
import com.ouyunc.message.http.annotation.RequestPart;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.multipart.Attribute;
import io.netty.handler.codec.http.multipart.FileUpload;
import io.netty.handler.codec.http.multipart.HttpData;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.nio.charset.StandardCharsets;

/**
 * 将 {@link HttpContext} 解析为控制器方法参数并调用（类似 Spring MVC 参数解析）。
 */
public final class HttpControllerMethodInvoker {

    private HttpControllerMethodInvoker() {
    }

    public static Object invoke(Object controller, Method method, HttpContext httpContext) throws Exception {
        method.setAccessible(true);
        Parameter[] parameters = method.getParameters();
        Object[] args = new Object[parameters.length];
        for (int i = 0; i < parameters.length; i++) {
            Parameter p = parameters[i];
            Class<?> t = p.getType();
            if (t == HttpContext.class) {
                args[i] = httpContext;
                continue;
            }
            if (t == ChannelHandlerContext.class) {
                args[i] = httpContext.getChannelContext();
                continue;
            }
            if (t == FullHttpRequest.class) {
                args[i] = httpContext.getRequest();
                continue;
            }
            if (p.isAnnotationPresent(PathVariable.class)) {
                PathVariable pv = p.getAnnotation(PathVariable.class);
                String name = HttpParamConverter.resolveName(p, pv.value());
                String raw = httpContext.getPathVariable(name);
                if (raw == null) {
                    if (pv.required()) {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "缺少路径变量: " + name);
                    }
                    args[i] = HttpParamConverter.missingOptionalValue(t);
                } else {
                    args[i] = HttpParamConverter.convert(raw, t);
                }
                continue;
            }
            if (p.isAnnotationPresent(RequestParam.class)) {
                RequestParam rp = p.getAnnotation(RequestParam.class);
                String name = HttpParamConverter.resolveName(p, rp.value());
                String raw = HttpUtil.getRequestParam(httpContext.getRequest(), name, httpContext.getFormUrlEncodedParams());
                if (raw == null && StringUtils.isNotEmpty(rp.defaultValue())) {
                    raw = rp.defaultValue();
                }
                if (raw == null) {
                    if (rp.required()) {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "缺少查询参数: " + name);
                    }
                    args[i] = HttpParamConverter.missingOptionalValue(t);
                } else {
                    args[i] = HttpParamConverter.convert(raw, t);
                }
                continue;
            }
            if (p.isAnnotationPresent(RequestPart.class)) {
                RequestPart rp = p.getAnnotation(RequestPart.class);
                String name = HttpParamConverter.resolveName(p, rp.value());
                HttpMultipartHolder holder = httpContext.getMultipart();
                if (holder == null) {
                    throw new IllegalStateException("路由未开启 multipart 解析却使用 @RequestPart: " + method);
                }
                var data = holder.getData(name);
                if (data == null) {
                    if (rp.required()) {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "缺少 multipart 表单项: " + name);
                    }
                    args[i] = HttpParamConverter.missingOptionalValue(t);
                    continue;
                }
                if (t == FileUpload.class || FileUpload.class.isAssignableFrom(t)) {
                    if (data instanceof FileUpload fu) {
                        args[i] = fu;
                    } else {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "表单项 " + name + " 不是文件域");
                    }
                    continue;
                }
                if (t == byte[].class) {
                    if (data instanceof HttpData hd) {
                        try {
                            args[i] = hd.get();
                        } catch (IOException ex) {
                            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                    "读取表单项失败: " + name + " — " + ex.getMessage());
                        }
                    } else {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "表单项 " + name + " 无法转为 byte[]");
                    }
                    continue;
                }
                if (t == String.class) {
                    try {
                        if (data instanceof Attribute attr) {
                            args[i] = attr.getValue();
                        } else if (data instanceof FileUpload fu) {
                            args[i] = new String(fu.get(), StandardCharsets.UTF_8);
                        } else {
                            throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                    "表单项 " + name + " 无法转为 String");
                        }
                    } catch (IOException ex) {
                        throw new HttpPipelineException(HttpResponseStatus.BAD_REQUEST, HttpResponseCodeEnum.BAD_REQUEST,
                                "读取表单项失败: " + name + " — " + ex.getMessage());
                    }
                    continue;
                }
                throw new IllegalStateException("不支持的 @RequestPart 参数类型: " + t.getName());
            }
            if (p.isAnnotationPresent(RequestBody.class)) {
                args[i] = httpContext.getBody();
                continue;
            }
            if (p.isAnnotationPresent(RequestHeader.class)) {
                String name = p.getAnnotation(RequestHeader.class).value();
                args[i] = httpContext.getRequest().headers().get(name);
                continue;
            }
            throw new IllegalStateException("不支持的参数: " + p + "，请使用 HttpContext、ChannelHandlerContext、FullHttpRequest、@PathVariable、@RequestParam、@RequestPart、@RequestBody、@RequestHeader");
        }
        try {
            return method.invoke(controller, args);
        } catch (InvocationTargetException e) {
            Throwable c = e.getCause();
            if (c instanceof Exception) {
                throw (Exception) c;
            }
            throw e;
        }
    }
}
