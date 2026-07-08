package com.ouyunc.message.http;

import com.ouyunc.message.http.auth.DefaultAppKeyHttpAuthenticator;

/**
 * 全局唯一 HTTP 鉴权器：业务通过 {@link #setGlobal(HttpRequestAuthenticator)} 注入（如 Token、appKey、签名等），
 * 未设置时默认为 {@link DefaultAppKeyHttpAuthenticator}。
 * <p>
 * 与 {@link com.ouyunc.message.http.annotation.IgnoreAuth} 配合：未标注忽略的路由都会走同一套全局鉴权。
 */
public final class HttpAuthenticators {

    private static volatile HttpRequestAuthenticator global = new DefaultAppKeyHttpAuthenticator();

    private HttpAuthenticators() {
    }

    /**
     * 替换全局鉴权实现；传入 {@code null} 则恢复为 {@link DefaultAppKeyHttpAuthenticator}。
     */
    public static void setGlobal(HttpRequestAuthenticator authenticator) {
        global = authenticator != null ? authenticator : new DefaultAppKeyHttpAuthenticator();
    }

    /**
     * 当前全局鉴权器（未调用过 {@link #setGlobal} 时为默认 appKey 校验）。
     */
    public static HttpRequestAuthenticator getGlobal() {
        return global;
    }
}
