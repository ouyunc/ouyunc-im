package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 请求会话
 */
public class RequestSession implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 请求会话id
     */
    private String sessionId;

    /**
     * 进度状态：
     * 请求进度
     * 请求进度，添加中 REQUEST_PROGRESS_JOINING = -1;
     * 请求进度，拒绝中 REQUEST_PROGRESS_REFUSEING = 0；
     * 请求进度，同意中 REQUEST_PROGRESS_AGREEING = 1;
     */
    private Integer progress;


    public RequestSession() {
    }

    public RequestSession(String sessionId, Integer progress) {
        this.sessionId = sessionId;
        this.progress = progress;
    }


    public String getSessionId() {
        return sessionId;
    }

    public void setSessionId(String sessionId) {
        this.sessionId = sessionId;
    }

    public Integer getProgress() {
        return progress;
    }

    public void setProgress(Integer progress) {
        this.progress = progress;
    }

    public static RequestSession.Builder newBuilder() {
        return new Builder();
    }
    public static class Builder {
        private String sessionId;
        private Integer progress;

        public Builder sessionId(String sessionId) {
            this.sessionId = sessionId;
            return this;
        }

        public Builder progress(Integer progress) {
            this.progress = progress;
            return this;
        }

        public RequestSession build() {
            return new RequestSession(sessionId, progress);
        }
    }
}
