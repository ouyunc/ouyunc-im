package com.ouyunc.message.safety;

import com.ouyunc.base.constant.enums.ContentSafetyHitType;
import com.ouyunc.base.constant.enums.TextModerationVendorEnum;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.message.properties.MessageServerProperties;
import com.tencentcloudapi.common.Credential;
import com.tencentcloudapi.common.exception.TencentCloudSDKException;
import com.tencentcloudapi.common.profile.ClientProfile;
import com.tencentcloudapi.common.profile.HttpProfile;
import com.tencentcloudapi.tms.v20201229.TmsClient;
import com.tencentcloudapi.tms.v20201229.models.TextModerationRequest;
import com.tencentcloudapi.tms.v20201229.models.TextModerationResponse;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

/**
 * 腾讯云文本内容安全 {@code TextModeration}。
 * <p>
 * 只在本地词库没有直接拒绝时调用。Block、Review 都拒绝发送。
 * 超时、限流、鉴权失败和网络故障抛出 {@link ContentSafetyUnavailableException}，禁止当成通过。
 * 文档：<a href="https://cloud.tencent.com/document/api/1124/51860">文本内容安全</a>。
 * </p>
 */
public final class TencentTextModerator implements TextModerationStrategy {

    private static final Logger log = LoggerFactory.getLogger(TencentTextModerator.class);

    /** 接口文档限制的 Unicode 字符数。 */
    static final int TEXT_MAX_CODE_POINTS = 10_000;

    /** DataId 最长 64，只允许字母数字和 _ - @ #。 */
    private static final int DATA_ID_MAX = 64;

    /** 回传命中词上限，避免日志被关键词列表撑大。 */
    private static final int KEYWORD_LIMIT = 8;

    /** 参数错误不会因为重试变好，按拒绝处理，避免同一条超长文本无限重试。 */
    private static final String INVALID_PARAMETER_PREFIX = "InvalidParameter";

    /** 复用客户端。密钥或超时变化后重建。 */
    private volatile TmsClient client;

    /** 与当前客户端对应的配置指纹，不含明文密钥。 */
    private volatile int clientStamp;

    TencentTextModerator() {
    }

    /**
     * @return 腾讯云
     */
    @Override
    public TextModerationVendorEnum vendor() {
        return TextModerationVendorEnum.TENCENT;
    }

    /**
     * 审核一段文本。由 {@link TextModerationStrategies} 在厂商为 TENCENT 时调用。
     *
     * @param text   原文。调用方保证非空白
     * @param dataId 业务消息 ID，只用于腾讯侧对账
     * @return 拒绝结果；通过时返回 null
     */
    @Override
    public ContentSafetyResult moderate(String text, String dataId) {
        return doModerate(text, dataId);
    }

    /**
     * 把处置建议映射成拒绝结果。Pass 返回 null。空建议或未知建议视为服务不可用。
     *
     * @param suggestion Block / Review / Pass
     * @param label      最高优先级标签，如 Polity、Porn、Terror
     * @param keywords   命中关键词，可为 null
     * @return 拒绝结果，或 null 表示通过
     */
    static ContentSafetyResult decide(String suggestion, String label, String[] keywords) {
        if (StringUtils.isBlank(suggestion)) {
            throw new ContentSafetyUnavailableException("腾讯文本审核没有返回处置建议");
        }
        if ("Pass".equalsIgnoreCase(suggestion)) {
            return null;
        }
        if ("Block".equalsIgnoreCase(suggestion) || "Review".equalsIgnoreCase(suggestion)) {
            ContentSafetyHitType hitType = "Polity".equalsIgnoreCase(label)
                    ? ContentSafetyHitType.POLITICS : ContentSafetyHitType.OTHER;
            return ContentSafetyResult.reject(hitType, keywordsOf(keywords), reason(suggestion, label));
        }
        throw new ContentSafetyUnavailableException("腾讯文本审核返回了未知处置建议");
    }

    /**
     * 超时、限流、鉴权、内部错误都要重试。参数错误重试不会成功。
     *
     * @param errorCode SDK 错误码，空表示网络层失败
     * @return true 时应暂缓受理
     */
    static boolean retryable(String errorCode) {
        return errorCode == null || !errorCode.startsWith(INVALID_PARAMETER_PREFIX);
    }

    /**
     * SDK 超时单位是秒，配置按毫秒写，最少 1 秒。
     *
     * @param timeoutMs 配置的毫秒
     * @return 秒
     */
    static int timeoutSeconds(int timeoutMs) {
        if (timeoutMs <= 0) {
            return 1;
        }
        return Math.max(1, (timeoutMs + 999) / 1000);
    }

    /**
     * 收成腾讯 DataId 允许的字符，超长截断。
     *
     * @param raw 原始消息 ID
     * @return 合法 DataId，无法构造时返回 null
     */
    static String dataId(String raw) {
        if (raw == null || raw.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder(Math.min(raw.length(), DATA_ID_MAX));
        for (int i = 0; i < raw.length() && builder.length() < DATA_ID_MAX; i++) {
            char ch = raw.charAt(i);
            if (isDataIdChar(ch)) {
                builder.append(ch);
            }
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private ContentSafetyResult doModerate(String text, String dataId) {
        MessageServerProperties properties = properties();
        if (text.codePointCount(0, text.length()) > TEXT_MAX_CODE_POINTS) {
            return ContentSafetyResult.reject(ContentSafetyHitType.OTHER, List.of(), "tms-text-too-long");
        }
        String secretId = StringUtils.trimToNull(properties.getTencentTmsSecretId());
        String secretKey = StringUtils.trimToNull(properties.getTencentTmsSecretKey());
        if (StringUtils.isAnyBlank(secretId, secretKey)) {
            log.error("腾讯文本审核已开启但未配置密钥");
            throw new ContentSafetyUnavailableException("腾讯文本审核未配置密钥");
        }
        try {
            TextModerationResponse response = client(properties, secretId, secretKey).TextModeration(request(text, dataId, properties));
            log.info("腾讯文本审核完成 requestId={} suggestion={} label={}",
                    response.getRequestId(), response.getSuggestion(), response.getLabel());
            return decide(response.getSuggestion(), response.getLabel(), response.getKeywords());
        } catch (ContentSafetyUnavailableException unavailable) {
            throw unavailable;
        } catch (TencentCloudSDKException sdkError) {
            return onSdkError(sdkError);
        } catch (RuntimeException failure) {
            log.warn("腾讯文本审核调用失败 type={}", failure.getClass().getSimpleName());
            throw new ContentSafetyUnavailableException("腾讯文本审核不可用", failure);
        }
    }

    /**
     * 参数错误拒绝本条；其余错误暂缓，避免故障期间把未审核文本发出去。
     *
     * @param sdkError SDK 异常
     * @return 仅参数错误时返回拒绝结果
     */
    private static ContentSafetyResult onSdkError(TencentCloudSDKException sdkError) {
        log.warn("腾讯文本审核失败 code={} requestId={}", sdkError.getErrorCode(), sdkError.getRequestId());
        if (!retryable(sdkError.getErrorCode())) {
            return ContentSafetyResult.reject(ContentSafetyHitType.OTHER, List.of(), "tms-bad-request");
        }
        throw new ContentSafetyUnavailableException("腾讯文本审核不可用");
    }

    private static TextModerationRequest request(String text, String rawDataId, MessageServerProperties properties) {
        TextModerationRequest request = new TextModerationRequest();
        request.setContent(Base64.getEncoder().encodeToString(text.getBytes(StandardCharsets.UTF_8)));
        request.setType("TEXT");
        String bizType = StringUtils.trimToNull(properties.getTencentTmsBizType());
        if (bizType != null) {
            request.setBizType(bizType);
        }
        String dataId = dataId(rawDataId);
        if (dataId != null) {
            request.setDataId(dataId);
        }
        return request;
    }

    private TmsClient client(MessageServerProperties properties, String secretId, String secretKey) {
        int stamp = clientStamp(properties, secretId, secretKey);
        TmsClient current = this.client;
        if (current != null && this.clientStamp == stamp) {
            return current;
        }
        synchronized (this) {
            if (this.client != null && this.clientStamp == stamp) {
                return this.client;
            }
            int seconds = timeoutSeconds(properties.getTextVendorTimeoutMs());
            HttpProfile http = new HttpProfile();
            http.setEndpoint(StringUtils.defaultIfBlank(properties.getTencentTmsEndpoint(), "tms.tencentcloudapi.com"));
            http.setConnTimeout(seconds);
            http.setReadTimeout(seconds);
            http.setWriteTimeout(seconds);
            ClientProfile profile = new ClientProfile();
            profile.setHttpProfile(http);
            String region = StringUtils.defaultIfBlank(properties.getTencentTmsRegion(), "ap-guangzhou");
            this.client = new TmsClient(new Credential(secretId, secretKey), region, profile);
            this.clientStamp = stamp;
            return this.client;
        }
    }

    private static int clientStamp(MessageServerProperties properties, String secretId, String secretKey) {
        return java.util.Objects.hash(secretId, secretKey, properties.getTencentTmsRegion(),
                properties.getTencentTmsEndpoint(), timeoutSeconds(properties.getTextVendorTimeoutMs()));
    }

    private static MessageServerProperties properties() {
        try {
            return MessageServerContext.serverProperties();
        } catch (RuntimeException unavailable) {
            throw new ContentSafetyUnavailableException("腾讯文本审核配置不可用", unavailable);
        }
    }

    private static List<String> keywordsOf(String[] keywords) {
        if (keywords == null || keywords.length == 0) {
            return List.of();
        }
        List<String> hits = new ArrayList<>(Math.min(keywords.length, KEYWORD_LIMIT));
        for (String keyword : keywords) {
            if (hits.size() >= KEYWORD_LIMIT || StringUtils.isBlank(keyword)) {
                continue;
            }
            hits.add(keyword.trim());
        }
        return List.copyOf(hits);
    }

    private static String reason(String suggestion, String label) {
        String action = "Review".equalsIgnoreCase(suggestion) ? "tms-review" : "tms-block";
        String token = safeToken(label);
        return token == null ? action : action + ":" + token;
    }

    /**
     * 标签只保留短的字母数字，避免把异常返回写进原因码。
     *
     * @param label 腾讯标签
     * @return 安全标签，空则 null
     */
    private static String safeToken(String label) {
        if (StringUtils.isBlank(label) || label.length() > 32) {
            return null;
        }
        for (int i = 0; i < label.length(); i++) {
            char ch = label.charAt(i);
            boolean ok = (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9');
            if (!ok) {
                return null;
            }
        }
        return label;
    }

    private static boolean isDataIdChar(char ch) {
        return (ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z') || (ch >= '0' && ch <= '9')
                || ch == '_' || ch == '-' || ch == '@' || ch == '#';
    }
}
