package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;

/**
 * LB 存活探活响应 data。
 * <p>对应 {@code GET /health}。</p>
 */
public class ServerHealthResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STATUS_UP = "UP";

    private String status;
    private String address;
    private boolean draining;

    public static ServerHealthResponse up(String address, boolean draining) {
        ServerHealthResponse response = new ServerHealthResponse();
        response.setStatus(STATUS_UP);
        response.setAddress(address);
        response.setDraining(draining);
        return response;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public boolean isDraining() {
        return draining;
    }

    public void setDraining(boolean draining) {
        this.draining = draining;
    }
}
