package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 商品卡片消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#PRODUCT_CARD_CONTENT}）。
 * <p>作为推荐商品发出时，可额外填写 {@link #recommendText}。</p>
 */
public class ProductCardContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 商品 ID（业务侧主键） */
    private String productId;
    /** SKU ID（可选，多规格时指定具体规格） */
    private String skuId;
    /** 商品标题/名称，气泡主文案 */
    private String title;
    /** 商品主图/缩略图 URL */
    private String thumbUrl;
    /** 展示价格字符串，如 "199.00"（发出时快照，不保证实时价） */
    private String price;
    /** 币种，如 CNY、USD */
    private String currency;
    /** 商品简介或卖点短文案（可选） */
    private String desc;
    /** 点击跳转商详的 H5 / DeepLink */
    private String link;
    /** 推荐语；坐席/用户推荐该商品时的说明文案（可选） */
    private String recommendText;

    public ProductCardContent() {
    }

    public String getProductId() {
        return productId;
    }

    public void setProductId(String productId) {
        this.productId = productId;
    }

    public String getSkuId() {
        return skuId;
    }

    public void setSkuId(String skuId) {
        this.skuId = skuId;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getThumbUrl() {
        return thumbUrl;
    }

    public void setThumbUrl(String thumbUrl) {
        this.thumbUrl = thumbUrl;
    }

    public String getPrice() {
        return price;
    }

    public void setPrice(String price) {
        this.price = price;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getDesc() {
        return desc;
    }

    public void setDesc(String desc) {
        this.desc = desc;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }

    public String getRecommendText() {
        return recommendText;
    }

    public void setRecommendText(String recommendText) {
        this.recommendText = recommendText;
    }
}
