package com.ouyunc.domain.base;

import java.io.Serializable;

/**
 * 请求会话
 */
public class RequestSession implements Serializable {

    /**
     * 请求会话id
     */
    private String sessionId;

    /**
     * 进度状态：
     * 好友请求进度
     * 好友请求进度，添加中 FRIEND_REQUEST_PROGRESS_JOINING = -1;
     * 好友请求进度，拒绝中 FRIEND_REQUEST_PROGRESS_REFUSEING = 0；
     * 好友请求进度，同意中 FRIEND_REQUEST_PROGRESS_AGREEING = 1;
     */
    private Integer progress;


    public RequestSession() {
    }

    public RequestSession(String sessionId, Integer progress) {
        this.sessionId = sessionId;
        this.progress = progress;
    }

    public RequestSession(Builder builder) {
        this.sessionId = builder.sessionId;
        this.progress = builder.progress;
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
            return new RequestSession(this);
        }
    }
}
