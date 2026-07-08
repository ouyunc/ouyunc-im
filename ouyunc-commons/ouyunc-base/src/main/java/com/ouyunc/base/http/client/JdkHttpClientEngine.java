package com.ouyunc.base.http.client;

import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** 基于 Java 11+ {@link java.net.http.HttpClient} 的实现。 */
public final class JdkHttpClientEngine implements HttpClientEngine {

    private final java.net.http.HttpClient client;
    private final HttpClientConfig config;

    public JdkHttpClientEngine(HttpClientConfig config) {
        this.config = config;
        this.client = java.net.http.HttpClient.newBuilder()
                .version(java.net.http.HttpClient.Version.HTTP_2)
                .connectTimeout(config.connectTimeout())
                .followRedirects(config.followRedirects()
                        ? java.net.http.HttpClient.Redirect.NORMAL : java.net.http.HttpClient.Redirect.NEVER)
                .build();
    }

    @Override
    public HttpClientResponse execute(HttpRequestSpec spec) {
        try {
            HttpRequest request = toRequest(spec);
            java.net.http.HttpResponse<byte[]> response = client.send(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray());
            return toResponse(response);
        } catch (HttpClientException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new HttpClientException("JDK HttpClient 请求失败: " + ex.getMessage(), 0, true, ex);
        }
    }

    @Override
    public CompletableFuture<HttpClientResponse> executeAsync(HttpRequestSpec spec) {
        HttpRequest request = toRequest(spec);
        return client.sendAsync(request, java.net.http.HttpResponse.BodyHandlers.ofByteArray())
                .thenApply(this::toResponse)
                .exceptionally(ex -> {
                    throw new HttpClientException("JDK HttpClient 异步请求失败: " + ex.getMessage(), 0, true, ex);
                });
    }

    @Override
    public void shutdown() {
        // JDK HttpClient 无需显式关闭
    }

    private HttpRequest toRequest(HttpRequestSpec spec) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(spec.url()))
                .timeout(config.readTimeout());

        byte[] bodyBytes = null;
        String multipartContentType = null;
        if (spec.isMultipart()) {
            MultipartBodyBuilder multipart = new MultipartBodyBuilder();
            for (HttpMultipartPart part : spec.multipartParts()) {
                multipart.addPart(part);
            }
            bodyBytes = multipart.build();
            multipartContentType = multipart.contentType();
        } else if (spec.body() != null) {
            bodyBytes = spec.body();
        }

        Map<String, String> headers = new LinkedHashMap<>(spec.headers());
        if (StringUtils.isNotBlank(spec.contentType()) && !headers.containsKey("Content-Type")) {
            headers.put("Content-Type", spec.contentType());
        }
        if (multipartContentType != null) {
            headers.put("Content-Type", multipartContentType);
        }
        headers.forEach(builder::header);

        HttpRequest.BodyPublisher publisher = publisherForMethod(spec.method(), bodyBytes);
        switch (spec.method()) {
            case GET -> builder.GET();
            case DELETE -> builder.method("DELETE", publisher);
            case POST -> builder.POST(publisher);
            case PUT -> builder.PUT(publisher);
            case PATCH -> builder.method("PATCH", publisher);
            case HEAD -> builder.method("HEAD", HttpRequest.BodyPublishers.noBody());
            case OPTIONS -> builder.method("OPTIONS", HttpRequest.BodyPublishers.noBody());
            default -> builder.method(spec.method().name(), publisher);
        }
        return builder.build();
    }

    private static HttpRequest.BodyPublisher publisherForMethod(HttpMethod method, byte[] bodyBytes) {
        if (method == HttpMethod.GET || method == HttpMethod.HEAD) {
            return HttpRequest.BodyPublishers.noBody();
        }
        if (bodyBytes == null) {
            return HttpRequest.BodyPublishers.noBody();
        }
        return HttpRequest.BodyPublishers.ofByteArray(bodyBytes);
    }

    private HttpClientResponse toResponse(java.net.http.HttpResponse<byte[]> response) {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        response.headers().map().forEach(headers::put);
        return new HttpClientResponse(response.statusCode(), headers, response.body());
    }
}
