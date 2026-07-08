package com.ouyunc.base.http.client;

/** 出站 HTTP 引擎类型。 */
public enum HttpClientEngineType {

    /** Java 11+ {@link java.net.http.HttpClient}，零额外依赖、原生 HTTP/2。 */
    JDK,

    /** Square OkHttp，连接池与拦截器生态成熟。 */
    OKHTTP
}
