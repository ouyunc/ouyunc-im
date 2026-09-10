package com.ouyunc.base.model;

import com.alibaba.fastjson2.JSON;
import org.apache.commons.lang3.StringUtils;

/**
 * IM 节点租约体，写入 {@code ouyunc:im:node:{nodeId}}。未上线，不做旧版纯 epoch 兼容。
 */
public class NodeLeasePayload {

    private long epoch;

    private String zoneId;

    private String addr;

    public NodeLeasePayload() {
    }

    public NodeLeasePayload(long epoch, String zoneId, String addr) {
        this.epoch = epoch;
        this.zoneId = zoneId;
        this.addr = addr;
    }

    public static NodeLeasePayload parse(String raw) {
        if (StringUtils.isBlank(raw)) {
            return null;
        }
        try {
            NodeLeasePayload payload = JSON.parseObject(raw.trim(), NodeLeasePayload.class);
            if (payload == null || payload.epoch <= 0L || StringUtils.isBlank(payload.addr)) {
                return null;
            }
            return payload;
        } catch (Exception ignored) {
            return null;
        }
    }

    public String toJson() {
        return JSON.toJSONString(this);
    }

    public long getEpoch() {
        return epoch;
    }

    public void setEpoch(long epoch) {
        this.epoch = epoch;
    }

    public String getZoneId() {
        return zoneId;
    }

    public void setZoneId(String zoneId) {
        this.zoneId = zoneId;
    }

    public String getAddr() {
        return addr;
    }

    public void setAddr(String addr) {
        this.addr = addr;
    }
}
