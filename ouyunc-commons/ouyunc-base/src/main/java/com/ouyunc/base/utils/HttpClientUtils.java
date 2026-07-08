package com.ouyunc.base.utils;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.ouyunc.base.http.client.*;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * 统一出站 HTTP 工具：支持 JDK {@link java.net.http.HttpClient} 与 OkHttp 双引擎，运行时可切换。
 * <p>
 * 性能参考（典型服务端 REST 调用，非绝对结论）：
 * <ul>
 *   <li><b>JDK HttpClient</b>：Java 21 + 虚拟线程 + HTTP/2 多路复用时，高并发下延迟与吞吐通常更优，且无第三方依赖。</li>
 *   <li><b>OkHttp</b>：连接池与拦截器生态成熟，Android/混合栈场景更常见；单连接极限吞吐与 JDK 接近，差异多在连接复用策略与 TLS 实现。</li>
 * </ul>
 * 默认使用 {@link HttpClientEngineType#JDK}；可通过 {@link #useEngine(HttpClientEngineType)} 切换。
 */
public final class HttpClientUtils {

    private static volatile HttpClientEngineType engineType = HttpClientEngineType.JDK;
    private static volatile HttpClientConfig config = HttpClientConfig.defaults();
    private static volatile HttpClientEngine engine;

    private HttpClientUtils() {
    }

    /** 切换引擎，下次请求生效。 */
    public static void useEngine(HttpClientEngineType type) {
        engineType = type == null ? HttpClientEngineType.JDK : type;
        resetEngine();
    }

    public static HttpClientEngineType currentEngine() {
        return engineType;
    }

    /** 更新全局超时等配置，下次请求生效。 */
    public static void configure(HttpClientConfig newConfig) {
        config = newConfig == null ? HttpClientConfig.defaults() : newConfig;
        resetEngine();
    }

    public static HttpClientConfig currentConfig() {
        return config;
    }

    public static void shutdown() {
        HttpClientEngine current = engine;
        if (current != null) {
            current.shutdown();
        }
        engine = null;
    }

    // ---- 通用执行 ----

    public static HttpClientResponse execute(HttpRequestSpec spec) {
        return engine().execute(spec);
    }

    public static HttpClientResponse executeOrThrow(HttpRequestSpec spec) {
        HttpClientResponse response = execute(spec);
        ensureSuccess(response);
        return response;
    }

    public static CompletableFuture<HttpClientResponse> executeAsync(HttpRequestSpec spec) {
        return engine().executeAsync(spec);
    }

    // ---- GET ----

    public static HttpClientResponse get(String url) {
        return execute(HttpRequestSpec.get(url).build());
    }

    public static HttpClientResponse get(String url, Map<String, String> headers) {
        return execute(HttpRequestSpec.get(url).headers(headers).build());
    }

    public static byte[] getBytes(String url) {
        return executeOrThrow(HttpRequestSpec.get(url).build()).bodyBytes();
    }

    public static byte[] getBytes(String url, String bearerToken) {
        return executeOrThrow(HttpRequestSpec.get(url).bearerToken(bearerToken).build()).bodyBytes();
    }

    public static JSONObject getJson(String url) {
        return getJson(url, null);
    }

    public static JSONObject getJson(String url, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.get(url);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return JSON.parseObject(executeOrThrow(builder.build()).bodyString());
    }

    // ---- POST / PUT / PATCH / DELETE ----

    public static HttpClientResponse postJson(String url, String jsonBody) {
        return postJson(url, jsonBody, null);
    }

    public static HttpClientResponse postJson(String url, String jsonBody, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.post(url).jsonBody(jsonBody);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build());
    }

    public static JSONObject postJsonObject(String url, String jsonBody, String bearerToken) {
        return JSON.parseObject(postJson(url, jsonBody, bearerToken).bodyString());
    }

    public static HttpClientResponse putJson(String url, String jsonBody, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.put(url).jsonBody(jsonBody);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build());
    }

    public static HttpClientResponse patchJson(String url, String jsonBody, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.patch(url).jsonBody(jsonBody);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build());
    }

    public static HttpClientResponse delete(String url) {
        return executeOrThrow(HttpRequestSpec.delete(url).build());
    }

    public static HttpClientResponse delete(String url, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.delete(url);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build());
    }

    // ---- 文件上传 / 下载 ----

    public static HttpClientResponse uploadMultipart(String url, List<HttpMultipartPart> parts, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.post(url).multipart(parts);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build());
    }

    public static HttpClientResponse uploadFile(String url, String fieldName, Path file, String bearerToken) {
        try {
            byte[] content = Files.readAllBytes(file);
            String filename = file.getFileName() != null ? file.getFileName().toString() : "file";
            String contentType = Files.probeContentType(file);
            HttpMultipartPart part = HttpMultipartPart.file(fieldName, filename, contentType, content);
            return uploadMultipart(url, List.of(part), bearerToken);
        } catch (IOException ex) {
            throw new HttpClientException("读取上传文件失败: " + ex.getMessage(), 0, false, ex);
        }
    }

    public static byte[] downloadBytes(String url) {
        return downloadBytes(url, null);
    }

    public static byte[] downloadBytes(String url, String bearerToken) {
        HttpRequestSpec.Builder builder = HttpRequestSpec.get(url);
        if (StringUtils.isNotBlank(bearerToken)) {
            builder.bearerToken(bearerToken);
        }
        return executeOrThrow(builder.build()).bodyBytes();
    }

    public static Path downloadToFile(String url, Path target, String bearerToken) {
        byte[] bytes = downloadBytes(url, bearerToken);
        try {
            if (target.getParent() != null) {
                Files.createDirectories(target.getParent());
            }
            return Files.write(target, bytes);
        } catch (IOException ex) {
            throw new HttpClientException("写入下载文件失败: " + ex.getMessage(), 0, false, ex);
        }
    }

    // ---- 内部 ----

    private static HttpClientEngine engine() {
        HttpClientEngine current = engine;
        if (current == null) {
            synchronized (HttpClientUtils.class) {
                current = engine;
                if (current == null) {
                    current = createEngine(engineType, config);
                    engine = current;
                }
            }
        }
        return current;
    }

    private static void resetEngine() {
        synchronized (HttpClientUtils.class) {
            HttpClientEngine old = engine;
            engine = null;
            if (old != null) {
                old.shutdown();
            }
        }
    }

    private static HttpClientEngine createEngine(HttpClientEngineType type, HttpClientConfig cfg) {
        return switch (type) {
            case OKHTTP -> new OkHttpClientEngine(cfg);
            case JDK -> new JdkHttpClientEngine(cfg);
        };
    }

    private static void ensureSuccess(HttpClientResponse response) {
        if (!response.isSuccessful()) {
            String preview = response.bodyString();
            if (preview.length() > 512) {
                preview = preview.substring(0, 512) + "...";
            }
            throw new HttpClientException(
                    "HTTP " + response.statusCode() + ": " + preview,
                    response.statusCode(),
                    response.statusCode() >= 500);
        }
    }
}
