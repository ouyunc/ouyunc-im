package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * HTTP 推送幂等占位。只在 HTTP 入口使用，写出 Kafka 前清空。
 */
public final class HttpPushClaim implements Serializable, Cloneable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** HTTP 幂等占位的执行令牌。只有当前持有者可以提交、失败标记或释放。 */
    private String httpPushOwnerToken;
    /** 规范化后的请求指纹。同一 messageId 不能换成另一份正文。 */
    private String httpPushPayloadHash;

    @Override
    public HttpPushClaim clone() {
        try {
            return (HttpPushClaim) super.clone();
        } catch (CloneNotSupportedException e) {
            throw new AssertionError(e);
        }
    }

    public void clear() {
        httpPushOwnerToken = null;
        httpPushPayloadHash = null;
    }

    public String getHttpPushOwnerToken() { return httpPushOwnerToken; }
    public void setHttpPushOwnerToken(String httpPushOwnerToken) { this.httpPushOwnerToken = httpPushOwnerToken; }
    public String getHttpPushPayloadHash() { return httpPushPayloadHash; }
    public void setHttpPushPayloadHash(String httpPushPayloadHash) { this.httpPushPayloadHash = httpPushPayloadHash; }
}
