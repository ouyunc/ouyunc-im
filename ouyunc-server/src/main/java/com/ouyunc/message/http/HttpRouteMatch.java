package com.ouyunc.message.http;

import java.util.Collections;
import java.util.Map;

/**
 * 一次路由解析结果：处理器 + 路径变量（无模板时变量为空）。
 */
public final class HttpRouteMatch {

    private final HttpRegisteredRoute route;

    private final Map<String, String> pathVariables;

    private HttpRouteMatch(HttpRegisteredRoute route, Map<String, String> pathVariables) {
        this.route = route;
        this.pathVariables = pathVariables;
    }

    public static HttpRouteMatch of(HttpRegisteredRoute route) {
        return new HttpRouteMatch(route, Collections.emptyMap());
    }

    public static HttpRouteMatch of(HttpRegisteredRoute route, Map<String, String> pathVariables) {
        return new HttpRouteMatch(route, pathVariables != null ? Map.copyOf(pathVariables) : Collections.emptyMap());
    }

    public HttpRegisteredRoute getRoute() {
        return route;
    }

    public Map<String, String> getPathVariables() {
        return pathVariables;
    }
}
