package com.ouyunc.base.model;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;

/**
 * LB 就绪探活响应 data。
 * <p>对应 {@code GET /ready}；不就绪时由控制器抛出 HTTP 503，不走本对象。</p>
 */
public class ServerReadyResponse implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    public static final String STATUS_READY = "READY";

    private String status;
    private String address;
    /** 非致命资源告警，可为空列表 */
    private List<String> warnings;

    public static ServerReadyResponse ready(String address, List<String> warnings) {
        ServerReadyResponse response = new ServerReadyResponse();
        response.setStatus(STATUS_READY);
        response.setAddress(address);
        response.setWarnings(warnings == null || warnings.isEmpty()
                ? Collections.emptyList()
                : List.copyOf(warnings));
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

    public List<String> getWarnings() {
        return warnings;
    }

    public void setWarnings(List<String> warnings) {
        this.warnings = warnings;
    }
}
