package com.ouyunc.message.processor.http.connection;

/**
 * 强制下线条件，各非空条件按 AND 组合。
 * <p>清空本节点全部连接必须显式传 {@code all=true}，避免空请求体误操作。</p>
 */
public class AdminConnectionOfflineRequest {

    private String appKey;
    private String identity;
    private Byte deviceType;
    private String channelId;
    private boolean all;
    private String reason;

    public String getAppKey() {
        return appKey;
    }

    public void setAppKey(String appKey) {
        this.appKey = appKey;
    }

    public String getIdentity() {
        return identity;
    }

    public void setIdentity(String identity) {
        this.identity = identity;
    }

    public Byte getDeviceType() {
        return deviceType;
    }

    public void setDeviceType(Byte deviceType) {
        this.deviceType = deviceType;
    }

    public String getChannelId() {
        return channelId;
    }

    public void setChannelId(String channelId) {
        this.channelId = channelId;
    }

    public boolean isAll() {
        return all;
    }

    public void setAll(boolean all) {
        this.all = all;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }
}
