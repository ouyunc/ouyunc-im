package com.ouyunc.base.http.client;

import org.apache.commons.lang3.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 出站 HTTP 请求描述。 */
public final class HttpRequestSpec {

    private final String url;
    private final HttpMethod method;
    private final Map<String, String> headers;
    private final byte[] body;
    private final String contentType;
    private final List<HttpMultipartPart> multipartParts;

    private HttpRequestSpec(Builder builder) {
        this.url = builder.url;
        this.method = builder.method;
        this.headers = Collections.unmodifiableMap(new LinkedHashMap<>(builder.headers));
        this.body = builder.body;
        this.contentType = builder.contentType;
        this.multipartParts = builder.multipartParts == null
                ? List.of() : List.copyOf(builder.multipartParts);
    }

    public static Builder get(String url) {
        return new Builder(url, HttpMethod.GET);
    }

    public static Builder post(String url) {
        return new Builder(url, HttpMethod.POST);
    }

    public static Builder put(String url) {
        return new Builder(url, HttpMethod.PUT);
    }

    public static Builder delete(String url) {
        return new Builder(url, HttpMethod.DELETE);
    }

    public static Builder patch(String url) {
        return new Builder(url, HttpMethod.PATCH);
    }

    public String url() {
        return url;
    }

    public HttpMethod method() {
        return method;
    }

    public Map<String, String> headers() {
        return headers;
    }

    public byte[] body() {
        return body;
    }

    public String contentType() {
        return contentType;
    }

    public List<HttpMultipartPart> multipartParts() {
        return multipartParts;
    }

    public boolean isMultipart() {
        return !multipartParts.isEmpty();
    }

    public static final class Builder {

        private final String url;
        private HttpMethod method;
        private final Map<String, String> headers = new LinkedHashMap<>();
        private byte[] body;
        private String contentType;
        private List<HttpMultipartPart> multipartParts;

        private Builder(String url, HttpMethod method) {
            if (StringUtils.isBlank(url)) {
                throw new IllegalArgumentException("url 不能为空");
            }
            this.url = url;
            this.method = method;
        }

        public Builder method(HttpMethod method) {
            if (method != null) {
                this.method = method;
            }
            return this;
        }

        public Builder header(String name, String value) {
            if (StringUtils.isNotBlank(name) && value != null) {
                headers.put(name, value);
            }
            return this;
        }

        public Builder headers(Map<String, String> headers) {
            if (headers != null) {
                headers.forEach(this::header);
            }
            return this;
        }

        public Builder bearerToken(String token) {
            if (StringUtils.isNotBlank(token)) {
                header("Authorization", "Bearer " + token);
            }
            return this;
        }

        public Builder jsonBody(String json) {
            contentType = "application/json; charset=UTF-8";
            body = StringUtils.defaultString(json).getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder textBody(String text, String contentType) {
            this.contentType = StringUtils.defaultIfBlank(contentType, "text/plain; charset=UTF-8");
            body = StringUtils.defaultString(text).getBytes(StandardCharsets.UTF_8);
            return this;
        }

        public Builder bytesBody(byte[] bytes, String contentType) {
            this.body = bytes == null ? new byte[0] : bytes;
            this.contentType = StringUtils.defaultIfBlank(contentType, "application/octet-stream");
            return this;
        }

        public Builder multipart(List<HttpMultipartPart> parts) {
            this.multipartParts = parts == null ? List.of() : new ArrayList<>(parts);
            this.body = null;
            this.contentType = null;
            return this;
        }

        public Builder multipart(HttpMultipartPart... parts) {
            if (parts == null || parts.length == 0) {
                return multipart(List.of());
            }
            return multipart(List.of(parts));
        }

        public HttpRequestSpec build() {
            return new HttpRequestSpec(this);
        }
    }
}
