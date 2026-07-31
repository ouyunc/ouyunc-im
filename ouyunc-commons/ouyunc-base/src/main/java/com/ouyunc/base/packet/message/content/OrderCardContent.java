package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 订单卡片消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#ORDER_CARD_CONTENT}）。
 */
public class OrderCardContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 订单号（业务侧订单主键/展示单号） */
    private String orderId;
    /** 订单卡片标题，如商品摘要「羽绒服等 2 件」 */
    private String title;
    /** 订单金额展示字符串，如 "299.00"（发出时快照） */
    private String amount;
    /** 币种，如 CNY、USD */
    private String currency;
    /** 订单业务状态码，如 PAID、SHIPPED、COMPLETED（机器可读） */
    private String status;
    /** 订单状态展示文案，如「已发货」「待付款」 */
    private String statusText;
    /** 订单首图/商品缩略图 URL（可选） */
    private String thumbUrl;
    /** 点击跳转订单详情的 H5 / DeepLink */
    private String link;

    public OrderCardContent() {
    }

    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAmount() {
        return amount;
    }

    public void setAmount(String amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
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

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
