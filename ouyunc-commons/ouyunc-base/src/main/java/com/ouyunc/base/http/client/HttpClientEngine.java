package com.ouyunc.base.http.client;

import java.util.concurrent.CompletableFuture;

/** 出站 HTTP 引擎 SPI。 */
public interface HttpClientEngine {

    HttpClientResponse execute(HttpRequestSpec request);

    CompletableFuture<HttpClientResponse> executeAsync(HttpRequestSpec request);

    void shutdown();
}
