package com.ouyunc.base.constant.enums;

/**
 * 敏感词分类（词库表 category / Redis Hash value 前缀）。
 */
public enum SensitiveWordCategoryEnum {

    /** 政治相关。 */
    POLITICS,
    /** 色情相关。 */
    PORN,
    /** 辱骂相关。 */
    ABUSE,
    /** 自定义。 */
    CUSTOM;

    /**
     * 解析字符串；空或非法返回 {@link #CUSTOM}。
     *
     * @param raw 原文
     * @return 枚举，永不 null
     */
    public static SensitiveWordCategoryEnum from(String raw) {
        return from(raw, CUSTOM);
    }

    /**
     * 解析字符串；空或非法返回 {@code defaultCategory}。
     *
     * @param raw             原文
     * @param defaultCategory 默认值
     * @return 枚举
     */
    public static SensitiveWordCategoryEnum from(String raw, SensitiveWordCategoryEnum defaultCategory) {
        if (raw == null || raw.isBlank()) {
            return defaultCategory;
        }
        try {
            return SensitiveWordCategoryEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultCategory;
        }
    }
}
