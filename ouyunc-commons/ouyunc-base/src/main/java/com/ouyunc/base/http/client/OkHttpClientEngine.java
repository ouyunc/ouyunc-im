package com.ouyunc.base.http.client;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** 基于 OkHttp 的实现。 */
public final class OkHttpClientEngine implements HttpClientEngine {

    private final OkHttpClient client;

    public OkHttpClientEngine(HttpClientConfig config) {
        this.client = new OkHttpClient.Builder()
                .connectTimeout(config.connectTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .readTimeout(config.readTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .writeTimeout(config.writeTimeout().toMillis(), TimeUnit.MILLISECONDS)
                .followRedirects(config.followRedirects())
                .connectionPool(new okhttp3.ConnectionPool(
                        config.maxIdleConnections(),
                        config.keepAliveDuration().toMillis(),
                        TimeUnit.MILLISECONDS))
                .build();
    }

    @Override
    public HttpClientResponse execute(HttpRequestSpec spec) {
        try (Response response = client.newCall(toRequest(spec)).execute()) {
            return toResponse(response);
        } catch (HttpClientException ex) {
            throw ex;
        } catch (IOException ex) {
            throw new HttpClientException("OkHttp 请求失败: " + ex.getMessage(), 0, true, ex);
        }
    }

    @Override
    public CompletableFuture<HttpClientResponse> executeAsync(HttpRequestSpec spec) {
        CompletableFuture<HttpClientResponse> future = new CompletableFuture<>();
        client.newCall(toRequest(spec)).enqueue(new okhttp3.Callback() {
            @Override
            public void onFailure(okhttp3.Call call, IOException e) {
                future.completeExceptionally(new HttpClientException("OkHttp 异步请求失败: " + e.getMessage(), 0, true, e));
            }

            @Override
            public void onResponse(okhttp3.Call call, Response response) {
                try (response) {
                    future.complete(toResponse(response));
                } catch (Exception ex) {
                    future.completeExceptionally(ex);
                }
            }
        });
        return future;
    }

    @Override
    public void shutdown() {
        client.dispatcher().executorService().shutdown();
        client.connectionPool().evictAll();
    }

    private Request toRequest(HttpRequestSpec spec) {
        RequestBody requestBody = buildRequestBody(spec);
        Request.Builder builder = new Request.Builder().url(spec.url());
        spec.headers().forEach(builder::addHeader);

        switch (spec.method()) {
            case GET -> builder.get();
            case DELETE -> {
                if (requestBody == null) {
                    builder.delete();
                } else {
                    builder.delete(requestBody);
                }
            }
            case POST -> builder.post(requestBody == null ? emptyBody() : requestBody);
            case PUT -> builder.put(requestBody == null ? emptyBody() : requestBody);
            case PATCH -> builder.patch(requestBody == null ? emptyBody() : requestBody);
            case HEAD -> builder.head();
            case OPTIONS -> builder.method("OPTIONS", requestBody == null ? emptyBody() : requestBody);
            default -> builder.method(spec.method().name(), requestBody == null ? emptyBody() : requestBody);
        }
        return builder.build();
    }

    private RequestBody buildRequestBody(HttpRequestSpec spec) {
        if (spec.method() == HttpMethod.GET || spec.method() == HttpMethod.HEAD) {
            return null;
        }
        if (spec.isMultipart()) {
            MultipartBody.Builder multipart = new MultipartBody.Builder().setType(MultipartBody.FORM);
            for (HttpMultipartPart part : spec.multipartParts()) {
                if (part.isFile()) {
                    MediaType mediaType = StringUtils.isNotBlank(part.contentType())
                            ? MediaType.parse(part.contentType()) : MediaType.parse("application/octet-stream");
                    multipart.addFormDataPart(part.name(), part.filename(),
                            RequestBody.create(part.content(), mediaType));
                } else {
                    multipart.addFormDataPart(part.name(),
                            new String(part.content(), StandardCharsets.UTF_8));
                }
            }
            return multipart.build();
        }
        byte[] body = spec.body() == null ? new byte[0] : spec.body();
        MediaType mediaType = StringUtils.isNotBlank(spec.contentType())
                ? MediaType.parse(spec.contentType()) : MediaType.parse("application/octet-stream");
        return RequestBody.create(body, mediaType);
    }

    private HttpClientResponse toResponse(Response response) throws IOException {
        Map<String, List<String>> headers = new LinkedHashMap<>();
        for (String name : response.headers().names()) {
            headers.put(name, response.headers().values(name));
        }
        ResponseBody body = response.body();
        byte[] bytes = body == null ? new byte[0] : body.bytes();
        return new HttpClientResponse(response.code(), headers, bytes);
    }

    private static RequestBody emptyBody() {
        return RequestBody.create(new byte[0], MediaType.parse("application/octet-stream"));
    }
}
