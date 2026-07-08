package com.ouyunc.message.http.auth;

import com.google.common.collect.Sets;

import java.util.Collections;
import java.util.Set;

/**
 * HTTP 鉴权主体：由 JWT 等解析出的调用方身份与权限。
 */
public final class HttpAuthPrincipal {

    private final String appKey;

    private final String identity;

    private final Integer fromType;

    private final Set<String> scopes;

    public HttpAuthPrincipal(String appKey, String identity, Integer fromType, Set<String> scopes) {
        this.appKey = appKey;
        this.identity = identity;
        this.fromType = fromType;
        this.scopes = scopes == null ? Sets.newHashSet() : Collections.unmodifiableSet(scopes);
    }

    public String getAppKey() {
        return appKey;
    }

    public String getIdentity() {
        return identity;
    }

    public Integer getFromType() {
        return fromType;
    }

    public Set<String> getScopes() {
        return scopes;
    }

    public boolean hasScope(String scope) {
        return scopes.contains(scope);
    }
}
