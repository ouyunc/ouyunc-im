package com.ouyunc.base.constant.enums;

/**
 * 媒体监黄供应商。
 */
public enum ContentSafetyProviderEnum {

    /** 阿里云内容安全。 */
    ALIYUN,
    /** 本地/联调 Mock，不调第三方。 */
    MOCK;

    /**
     * 解析字符串；空或非法返回 {@code defaultProvider}。
     *
     * @param raw             原文
     * @param defaultProvider 默认值
     * @return 枚举
     */
    public static ContentSafetyProviderEnum from(String raw, ContentSafetyProviderEnum defaultProvider) {
        if (raw == null || raw.isBlank()) {
            return defaultProvider;
        }
        try {
            return ContentSafetyProviderEnum.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return defaultProvider;
        }
    }
}
