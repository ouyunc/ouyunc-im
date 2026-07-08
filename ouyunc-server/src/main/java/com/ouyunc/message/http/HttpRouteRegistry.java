package com.ouyunc.message.http;

import com.ouyunc.base.constant.HttpConstant;
import com.ouyunc.base.utils.ClassScannerUtil;
import com.ouyunc.message.http.annotation.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 扫描并注册 HTTP 路由：旧版 {@link HttpRequestProcessor} 与 {@link HttpRestController} 方法映射。
 * 支持精确路径与含 {@code {name}} 的路径模板。
 */
public class HttpRouteRegistry {
    private static final Logger log = LoggerFactory.getLogger(HttpRouteRegistry.class);

    private final Map<String, HttpRegisteredRoute> exactRoutes = new ConcurrentHashMap<>();

    private final HttpPatternRouteTrie patternTrie = new HttpPatternRouteTrie();

    /** 启动扫描阶段登记的路由行，用于一次性汇总日志 */
    private final List<String> startupRouteLines = new ArrayList<>();

    /**
     * 先查精确路径，再在路径模板前缀树中匹配（字面量优先于 {@code {var}}）。
     */
    public HttpRouteMatch find(String method, String path) {
        String npath = normalizePath(path);
        HttpRegisteredRoute exact = exactRoutes.get(key(method, npath));
        if (exact != null) {
            return HttpRouteMatch.of(exact);
        }
        Map<String, String> vars = new HashMap<>();
        HttpRegisteredRoute matched = patternTrie.match(method, HttpPathTemplateMatcher.segments(npath), vars);
        if (matched != null) {
            return HttpRouteMatch.of(matched, vars);
        }
        return null;
    }

    public void registerLegacy(String httpMethod, String path, HttpRequestProcessor<?> processor) {
        HttpRouteDescriptor descriptor = HttpRouteDescriptor.forLegacy(processor);
        putRoute(httpMethod, path, descriptor, processor);
    }

    private void putRoute(String httpMethod, String path, HttpRouteDescriptor descriptor, HttpRequestProcessor<?> processor) {
        String np = normalizePath(path);
        HttpRegisteredRoute route = new HttpRegisteredRoute(descriptor, processor);
        if (HttpPathTemplate.isTemplate(np)) {
            patternTrie.add(httpMethod, np, route);
            startupRouteLines.add(httpMethod.toUpperCase() + " " + np);
            log.debug("Registered HTTP {} {} (path template)", httpMethod, np);
            return;
        }
        String k = key(httpMethod, np);
        HttpRegisteredRoute prev = exactRoutes.put(k, route);
        if (prev != null) {
            log.warn("HTTP route {} {} 被覆盖注册", httpMethod, np);
        }
        startupRouteLines.add(httpMethod.toUpperCase() + " " + np);
        log.debug("Registered HTTP {} {}", httpMethod, np);
    }

    /**
     * 打印当前已登记的全部 HTTP 路由（method + path），一般在完成包扫描后调用一次。
     */
    public void logStartupRouteSummary() {
        if (startupRouteLines.isEmpty()) {
            log.info("已注册 HTTP 接口: 无");
            return;
        }
        List<String> sorted = startupRouteLines.stream().sorted().toList();
        log.info("已注册 HTTP 接口 (共 {} 个)", sorted.size());
    }

    public void scanAndRegister(String basePackage) {
        try {
            scanLegacyProcessors(basePackage);
            scanHttpControllers(basePackage);
        } catch (IOException e) {
            log.error("Scan HTTP routes failed: {}", e.getMessage());
        }
    }

    private void scanLegacyProcessors(String basePackage) throws IOException {
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
            if (httpMethod == null || path == null || path.isEmpty()) {
                continue;
            }
            try {
                HttpRequestProcessor<?> handler = (HttpRequestProcessor<?>) clazz.getDeclaredConstructor().newInstance();
                registerLegacy(httpMethod, path, handler);
            } catch (Exception e) {
                log.warn("Failed to instantiate HTTP processor {}: {}", clazz.getName(), e.getMessage());
            }
        }
    }

    private void scanHttpControllers(String basePackage) throws IOException {
        Set<Class<?>> classes = ClassScannerUtil.scanPackage(basePackage, clazz ->
                clazz.isAnnotationPresent(HttpRestController.class)
                        && !clazz.isInterface()
                        && !Modifier.isAbstract(clazz.getModifiers()));
        for (Class<?> clazz : classes) {
            String basePath = "";
            if (clazz.isAnnotationPresent(HttpRequestMapping.class)) {
                basePath = clazz.getAnnotation(HttpRequestMapping.class).value();
            }
            Object controller;
            try {
                controller = clazz.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                log.warn("Failed to instantiate HTTP controller {}: {}", clazz.getName(), e.getMessage());
                continue;
            }
            for (Method method : clazz.getDeclaredMethods()) {
                if (!Modifier.isPublic(method.getModifiers())) {
                    continue;
                }
                Route route = resolveRoute(method);
                if (route == null) {
                    continue;
                }
                String fullPath = combinePaths(basePath, route.path);
                fullPath = normalizePath(fullPath);
                if (fullPath.isEmpty()) {
                    log.warn("Skip empty path on {}#{}", clazz.getSimpleName(), method.getName());
                    continue;
                }
                try {
                    HttpRouteDescriptor descriptor = HttpRouteDescriptor.forControllerMethod(clazz, method);
                    HttpRequestProcessor<?> processor = httpContext -> HttpControllerMethodInvoker.invoke(controller, method, httpContext);
                    putRoute(route.httpMethod, fullPath, descriptor, processor);
                } catch (IllegalStateException ex) {
                    log.error("Invalid HTTP controller method {}#{}: {}", clazz.getName(), method.getName(), ex.getMessage());
                }
            }
        }
    }

    private static Route resolveRoute(Method method) {
        if (method.isAnnotationPresent(PostHttpRequest.class)) {
            return new Route(HttpConstant.POST, method.getAnnotation(PostHttpRequest.class).value());
        }
        if (method.isAnnotationPresent(GetHttpRequest.class)) {
            return new Route(HttpConstant.GET, method.getAnnotation(GetHttpRequest.class).value());
        }
        if (method.isAnnotationPresent(HttpRequest.class)) {
            HttpRequest hr = method.getAnnotation(HttpRequest.class);
            return new Route(hr.method(), hr.path());
        }
        return null;
    }

    private static String combinePaths(String base, String path) {
        String b = base == null ? "" : base.trim();
        String p = path == null ? "" : path.trim();
        if (b.isEmpty()) {
            return p;
        }
        if (p.isEmpty()) {
            return b;
        }
        while (b.endsWith("/")) {
            b = b.substring(0, b.length() - 1);
        }
        if (!p.startsWith("/")) {
            p = "/" + p;
        }
        return b + p;
    }

    private static String key(String method, String path) {
        return method.toUpperCase() + " " + normalizePath(path);
    }

    private static String normalizePath(String path) {
        if (path == null) {
            return "/";
        }
        path = path.trim();
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        while (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return path;
    }

    private static final class Route {
        final String httpMethod;
        final String path;

        Route(String httpMethod, String path) {
            this.httpMethod = httpMethod;
            this.path = path;
        }
    }
}
