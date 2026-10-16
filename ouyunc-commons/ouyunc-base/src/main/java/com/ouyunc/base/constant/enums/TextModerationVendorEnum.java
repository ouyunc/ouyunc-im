package com.ouyunc.base.constant.enums;

import java.util.Locale;

/**
 * 文本内容安全厂商。媒体监黄供应商见 {@link ContentSafetyProviderEnum}，两者分开配置。
 */
public enum TextModerationVendorEnum {

    /** 不调用第三方，只使用本地词库。 */
    NONE,
    /** 腾讯云文本内容安全。 */
    TENCENT,
    /** 阿里云内容安全。策略已预留，接入前不得放行。 */
    ALIYUN,
    /** 数美文本审核。策略已预留，接入前不得放行。 */
    SHUMEI;

    /**
     * 解析配置。空白视为 {@link #NONE}。无法识别时返回 null，调用方必须暂缓受理，不能当成关闭。
     *
     * @param raw 配置原文，忽略大小写
     * @return 厂商；未知时为 null
     */
    public static TextModerationVendorEnum parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return NONE;
        }
        try {
            return valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
