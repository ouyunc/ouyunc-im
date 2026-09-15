package com.ouyunc.base.constant.enums;

/**
 * 内容安全命中类型，写入审计与审核日志，便于运营分类。
 */
public enum ContentSafetyHitType {

    /** 本地敏感词库命中。 */
    KEYWORD,
    /** 图片监黄命中（P1）。 */
    IMAGE_PORN,
    /** 视频监黄命中（P1）。 */
    VIDEO_PORN,
    /** 政治类（预留，可由词库 category 映射）。 */
    POLITICS,
    /** 其它/未分类。 */
    OTHER
}
