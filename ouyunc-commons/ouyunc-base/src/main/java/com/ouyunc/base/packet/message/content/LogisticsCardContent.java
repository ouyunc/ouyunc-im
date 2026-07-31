package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 物流卡片消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#LOGISTICS_CARD_CONTENT}）。
 */
public class LogisticsCardContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 关联订单号（可选，便于跳转订单） */
    private String orderId;
    /** 物流运单号 */
    private String trackingNo;
    /** 承运商名称或编码，如「顺丰」、SF */
    private String carrier;
    /** 物流业务状态码，如 IN_TRANSIT、DELIVERED（机器可读） */
    private String status;
    /** 物流状态展示文案，如「运输中」「已签收」 */
    private String statusText;
    /** 最新一条轨迹摘要，如「已到达【上海转运中心】」 */
    private String latestTrace;
    /** 轨迹/状态更新时间（毫秒时间戳，可选） */
    private Long updatedAt;
    /** 点击跳转物流详情的 H5 / DeepLink */
    private String link;

    public LogisticsCardContent() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTrackingNo() {
        return trackingNo;
    }

    public void setTrackingNo(String trackingNo) {
        this.trackingNo = trackingNo;
    }

    public String getCarrier() {
        return carrier;
    }

    public void setCarrier(String carrier) {
        this.carrier = carrier;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getStatusText() {
        return statusText;
    }

    public void setStatusText(String statusText) {
        this.statusText = statusText;
    }

    public String getLatestTrace() {
        return latestTrace;
    }

    public void setLatestTrace(String latestTrace) {
        this.latestTrace = latestTrace;
    }

    public Long getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Long updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
