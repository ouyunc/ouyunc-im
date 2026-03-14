package com.ouyunc.message.http;

import com.ouyunc.base.constant.HttpConstant;
import com.ouyunc.base.utils.ClassScannerUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 扫描并注册 HTTP 处理器：仅扫描实现 HttpRequestProcessor 且类上带 @GetMapping / @PostMapping / @HttpRoute 的类。
 */
public class HttpRequestProcessorRegistrar {
    private static final Logger log = LoggerFactory.getLogger(HttpRequestProcessorRegistrar.class);

    /** key: "METHOD path" */
    private final Map<String, HttpRequestProcessor> processors = new ConcurrentHashMap<>();

    public void register(String method, String path, HttpRequestProcessor handler) {
        String key = key(method, path);
        processors.put(key, handler);
        log.info("Registered HTTP {} {}", method, path);
    }

    public HttpRequestProcessor find(String method, String path) {
        return processors.get(key(method, path));
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + " " + normalizePath(path);
    }

    private static String normalizePath(String path) {
        if (path == null) return "/";
        path = path.trim();
        if (!path.startsWith("/")) path = "/" + path;
        while (path.length() > 1 && path.endsWith("/")) path = path.substring(0, path.length() - 1);
        return path;
    }

    /**
     * 扫描指定包，注册所有实现 HttpRequestProcessor 且带类级别路由注解的类。
     */
    public void scanAndRegister(String basePackage) {
        try {
            Set<Class<?>> classes = ClassScannerUtil.scanPackageBySuper(basePackage, HttpRequestProcessor.class);
            for (Class<?> clazz : classes) {
                String httpMethod = null;
                String path = null;
                if (clazz.isAnnotationPresent(GetHttpRequest.class)) {
                    GetHttpRequest get = clazz.getAnnotation(GetHttpRequest.class);
                    httpMethod = HttpConstant.GET;
                    path = get.value();
                } else if (clazz.isAnnotationPresent(PostHttpRequest.class)) {
                    PostHttpRequest post = clazz.getAnnotation(PostHttpRequest.class);
                    httpMethod = HttpConstant.POST;
                    path = post.value();
                } else if (clazz.isAnnotationPresent(HttpRequest.class)) {
                    HttpRequest httpRequest = clazz.getAnnotation(HttpRequest.class);
                    httpMethod = httpRequest.method();
                    path = httpRequest.path();
                }
                if (httpMethod == null || path == null || path.isEmpty()) continue;
                try {
                    HttpRequestProcessor handler = (HttpRequestProcessor) clazz.getDeclaredConstructor().newInstance();
                    register(httpMethod, path, handler);
                } catch (Exception e) {
                    log.warn("Failed to instantiate HTTP processor {}: {}", clazz.getName(), e.getMessage());
                }
            }
        } catch (IOException e) {
            log.error("Scan HTTP handlers failed: {}", e.getMessage());
        }
    }
}
