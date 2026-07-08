package com.ouyunc.base.http.client;

import java.time.Duration;

/**
 * HTTP 客户端全局配置。
 */
public final class HttpClientConfig {

    public static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(10);
    public static final Duration DEFAULT_READ_TIMEOUT = Duration.ofSeconds(30);
    public static final Duration DEFAULT_WRITE_TIMEOUT = Duration.ofSeconds(30);

    private final Duration connectTimeout;
    private final Duration readTimeout;
    private final Duration writeTimeout;
    private final boolean followRedirects;
    private final int maxIdleConnections;
    private final Duration keepAliveDuration;

    private HttpClientConfig(Builder builder) {
        this.connectTimeout = builder.connectTimeout;
        this.readTimeout = builder.readTimeout;
        this.writeTimeout = builder.writeTimeout;
        this.followRedirects = builder.followRedirects;
        this.maxIdleConnections = builder.maxIdleConnections;
        this.keepAliveDuration = builder.keepAliveDuration;
    }

    public static HttpClientConfig defaults() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Duration connectTimeout() {
        return connectTimeout;
    }

    public Duration readTimeout() {
        return readTimeout;
    }

    public Duration writeTimeout() {
        return writeTimeout;
    }

    public boolean followRedirects() {
        return followRedirects;
    }

    public int maxIdleConnections() {
        return maxIdleConnections;
    }

    public Duration keepAliveDuration() {
        return keepAliveDuration;
    }

    public static final class Builder {

        private Duration connectTimeout = DEFAULT_CONNECT_TIMEOUT;
        private Duration readTimeout = DEFAULT_READ_TIMEOUT;
        private Duration writeTimeout = DEFAULT_WRITE_TIMEOUT;
        private boolean followRedirects = true;
        private int maxIdleConnections = 32;
        private Duration keepAliveDuration = Duration.ofMinutes(5);

        public Builder connectTimeout(Duration connectTimeout) {
            if (connectTimeout != null) {
                this.connectTimeout = connectTimeout;
            }
            return this;
        }

        public Builder readTimeout(Duration readTimeout) {
            if (readTimeout != null) {
                this.readTimeout = readTimeout;
            }
            return this;
        }

        public Builder writeTimeout(Duration writeTimeout) {
            if (writeTimeout != null) {
                this.writeTimeout = writeTimeout;
            }
            return this;
        }

        public Builder followRedirects(boolean followRedirects) {
            this.followRedirects = followRedirects;
            return this;
        }

        public Builder maxIdleConnections(int maxIdleConnections) {
            if (maxIdleConnections > 0) {
                this.maxIdleConnections = maxIdleConnections;
            }
            return this;
        }

        public Builder keepAliveDuration(Duration keepAliveDuration) {
            if (keepAliveDuration != null) {
                this.keepAliveDuration = keepAliveDuration;
            }
            return this;
        }

        public HttpClientConfig build() {
            return new HttpClientConfig(this);
        }
    }
}
