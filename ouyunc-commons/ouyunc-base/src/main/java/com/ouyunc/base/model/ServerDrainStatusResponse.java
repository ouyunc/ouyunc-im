package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * 运维摘流 / 取消摘流接口响应 data。
 * <p>对应 {@code POST /admin/drain}、{@code POST /admin/undrain}。</p>
 */
public class ServerDrainStatusResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 是否处于摘流中 */
    private boolean draining;

    /** 运行期是否仍接受新登录（摘流时为 false） */
    private boolean acceptNewConnections;

    /** 本节点地址 ip:port */
    private String address;

    /** 就绪探活路径，如 /ready */
    private String readyPath;

    public static ServerDrainStatusResponse of(boolean draining, boolean acceptNewConnections,
                                               String address, String readyPath) {
        ServerDrainStatusResponse response = new ServerDrainStatusResponse();
        response.setDraining(draining);
        response.setAcceptNewConnections(acceptNewConnections);
        response.setAddress(address);
        response.setReadyPath(readyPath);
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
}
