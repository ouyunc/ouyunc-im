package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.TextModerationVendorEnum;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 按配置选择文本审核厂商。未接入的厂商不会静默放行。
 */
public final class TextModerationStrategies {

    private static final Logger log = LoggerFactory.getLogger(TextModerationStrategies.class);

    /** 腾讯实现有客户端缓存，进程内只保留一份。 */
    private static final TencentTextModerator TENCENT = new TencentTextModerator();

    private TextModerationStrategies() {
    }

    /**
     * 按当前配置审核。未启用时返回 null。
     *
     * @param text   原文
     * @param dataId 报文 packetId
     * @return 拒绝结果；不审核或厂商通过时返回 null
     */
    public static ContentSafetyResult moderate(String text, String dataId) {
        TextModerationStrategy strategy = current();
        if (strategy == null) {
            return null;
        }
        return strategy.moderate(text, dataId);
    }

    /**
     * 把配置解析成策略。空白和 NONE 不审核。未知或尚未接入的厂商抛出不可用。
     *
     * @param provider 配置中的厂商名
     * @return 策略；NONE 时为 null
     */
    static TextModerationStrategy select(String provider) {
        TextModerationVendorEnum vendor = TextModerationVendorEnum.parse(provider);
        if (vendor == null) {
            log.error("未知文本审核厂商 provider={}", provider);
            throw new ContentSafetyUnavailableException("未知文本审核厂商");
        }
        return switch (vendor) {
            case NONE -> null;
            case TENCENT -> TENCENT;
            case ALIYUN, SHUMEI -> new PendingTextModerationStrategy(vendor);
        };
    }

    private static TextModerationStrategy current() {
        try {
            MessageServerProperties properties = MessageServerContext.serverProperties();
            return select(properties.getTextVendorProvider());
        } catch (ContentSafetyUnavailableException unavailable) {
            throw unavailable;
        } catch (RuntimeException failure) {
            log.warn("读取文本审核厂商配置失败，跳过本次第三方审核");
            return null;
        }
    }

    /**
     * 已列入策略、但 SDK 还没接上的厂商。调用即暂缓，避免把未审核文本发出去。
     */
    private static final class PendingTextModerationStrategy implements TextModerationStrategy {

        private final TextModerationVendorEnum vendor;

        private PendingTextModerationStrategy(TextModerationVendorEnum vendor) {
            this.vendor = vendor;
        }

        @Override
        public TextModerationVendorEnum vendor() {
            return vendor;
        }

        @Override
        public ContentSafetyResult moderate(String text, String dataId) {
            log.error("文本审核厂商尚未接入 vendor={}", vendor);
            throw new ContentSafetyUnavailableException("文本审核厂商尚未接入");
        }
    }
}
