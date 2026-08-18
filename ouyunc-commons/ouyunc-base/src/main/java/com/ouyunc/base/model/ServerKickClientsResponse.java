package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维通知客户端主动重连接口响应 data。
 * <p>对应 {@code POST /admin/kick-clients}：服务端不下发 close，由客户端主动断开。</p>
 */
public class ServerKickClientsResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 客户端主动断开模式标识 */
    public static final String MODE_CLIENT_ACTIVE_DISCONNECT = "client-active-disconnect";

    private boolean draining;
    private boolean acceptNewConnections;
    private String address;
    private String readyPath;
    /** 成功下发重连通知的连接数 */
    private int notified;
    /** 通知后本机注册表仍残留的在线数（客户端尚未断开时可能大于 0） */
    private int localOnlineRemaining;
    /** 踢线模式，固定为 {@link #MODE_CLIENT_ACTIVE_DISCONNECT} */
    private String mode;

    public static ServerKickClientsResponse of(ServerDrainStatusResponse drainStatus,
                                               int notified, int localOnlineRemaining) {
        ServerKickClientsResponse response = new ServerKickClientsResponse();
        response.setDraining(drainStatus.isDraining());
        response.setAcceptNewConnections(drainStatus.isAcceptNewConnections());
        response.setAddress(drainStatus.getAddress());
        response.setReadyPath(drainStatus.getReadyPath());
        response.setNotified(notified);
        response.setLocalOnlineRemaining(localOnlineRemaining);
        response.setMode(MODE_CLIENT_ACTIVE_DISCONNECT);
        return response;
    }

    public boolean isDraining() {
        return draining;
    }

    public void setDraining(boolean draining) {
        this.draining = draining;
    }

    public boolean isAcceptNewConnections() {
        return acceptNewConnections;
    }

    public void setAcceptNewConnections(boolean acceptNewConnections) {
        this.acceptNewConnections = acceptNewConnections;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public String getReadyPath() {
        return readyPath;
    }

    public void setReadyPath(String readyPath) {
        this.readyPath = readyPath;
    }

    public int getNotified() {
        return notified;
    }

    public void setNotified(int notified) {
        this.notified = notified;
    }

    public int getLocalOnlineRemaining() {
        return localOnlineRemaining;
    }

    public void setLocalOnlineRemaining(int localOnlineRemaining) {
        this.localOnlineRemaining = localOnlineRemaining;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }
}
