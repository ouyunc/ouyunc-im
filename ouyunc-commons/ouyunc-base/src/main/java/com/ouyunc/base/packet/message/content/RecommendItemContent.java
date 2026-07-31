package com.ouyunc.base.packet.message.content;

import java.io.Serial;
import java.io.Serializable;

/**
 * 通用推荐物消息内容（{@link com.ouyunc.base.constant.enums.MessageContentTypeEnum#RECOMMEND_ITEM_CONTENT}）。
 * <p>用于文章/门店/优惠券等非商品推荐；商品请使用 {@link ProductCardContent}。</p>
 */
public class RecommendItemContent implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    /** 推荐对象类型：article（文章）/ store（门店）/ coupon（优惠券）等 */
    private String refType;
    /** 推荐对象在业务侧的主键 ID */
    private String refId;
    /** 卡片主标题 */
    private String title;
    /** 封面/缩略图 URL */
    private String thumbUrl;
    /** 副标题或补充说明，如价格区间、有效期 */
    private String subtitle;
    /** 推荐语，说明为何推荐该内容（可选） */
    private String recommendText;
    /** 点击跳转详情的 H5 / DeepLink */
    private String link;

    public RecommendItemContent() {
    }

    public String getRefType() {
        return refType;
    }

    public void setRefType(String refType) {
        this.refType = refType;
    }

    public String getRefId() {
        return refId;
    }

    public void setRefId(String refId) {
        this.refId = refId;
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

    public String getSubtitle() {
        return subtitle;
    }

    public void setSubtitle(String subtitle) {
        this.subtitle = subtitle;
    }

    public String getRecommendText() {
        return recommendText;
    }

    public void setRecommendText(String recommendText) {
        this.recommendText = recommendText;
    }

    public String getLink() {
        return link;
    }

    public void setLink(String link) {
        this.link = link;
    }
}
