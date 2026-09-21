package com.ouyunc.message.safety;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.CacheConstant;
import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.constant.enums.ContentSafetyHitType;
import com.ouyunc.base.constant.enums.ContentSafetyReasonEnum;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
import com.ouyunc.base.constant.enums.ModerationModeEnum;
import com.ouyunc.base.constant.enums.ModerationStatusEnum;
import com.ouyunc.base.model.ContentSafetyPolicy;
import com.ouyunc.base.model.ContentSafetyResult;
import com.ouyunc.base.model.Metadata;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.base.packet.message.Message;
import com.ouyunc.base.packet.message.content.ImageTextContent;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 内容安全统一入口（WS / 原生 Packet / HTTP Push 共用）。
 * <p>P0：文本/图文说明做敏感词 MASK、REJECT、AUDIT_ONLY；图片视频仅打 metadata 标记，监黄闭环见 P1。
 * 生产日志只打 hits，不打原文全文。</p>
 */
public final class ContentSafetyFacade {

    /** 日志。 */
    private static final Logger log = LoggerFactory.getLogger(ContentSafetyFacade.class);

    /**
     * 工具类，禁止实例化。
     */
    private ContentSafetyFacade() {
    }

    /**
     * 检查并可能原地改写 {@code message.content}（MASK）。
     *
     * @param packet 协议包
     * @return 检查结果；REJECT 时调用方不得继续 fire / 落库
     */
    public static ContentSafetyResult check(Packet packet) {
        if (packet == null || packet.getMessage() == null) {
            return ContentSafetyResult.pass();
        }
        ContentSafetyRegistry registry = ContentSafetyRegistry.getInstance();
        if (!registry.isEnabled()) {
            return ContentSafetyResult.pass();
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata == null ? null : metadata.getAppKey();
        int contentType = message.getContentType();
        if (StringUtils.isBlank(appKey)) {
            if (contentType == MessageContentTypeEnum.TEXT_CONTENT.getType()
                    || contentType == MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType()) {
                appKey = CacheConstant.CONTENT_SAFETY_GLOBAL_APP_KEY;
            } else {
                return ContentSafetyResult.pass();
            }
        }
        ContentSafetyPolicy policy = registry.policy(appKey);

        // 文本 / 图文说明
        if (contentType == MessageContentTypeEnum.TEXT_CONTENT.getType()) {
            return checkPlainText(message, appKey, policy, registry);
        }
        if (contentType == MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType()) {
            return checkImageText(message, appKey, policy, registry);
        }

        // 媒体：P0 放行，写标记供 P1 监黄使用
        if (policy.isMediaEnabled()
                && (contentType == MessageContentTypeEnum.IMAGE_CONTENT.getType()
                || contentType == MessageContentTypeEnum.VIDEO_CONTENT.getType())) {
            if (metadata != null && metadata.getModerationStatus() == null) {
                metadata.setModerationStatus(ModerationStatusEnum.NONE);
                metadata.setModerationMode(ModerationModeEnum.fromAction(
                        policy.getMediaAction(), ModerationModeEnum.SEND_THEN_REVIEW));
            }
        }
        return ContentSafetyResult.pass();
    }

    /**
     * 纯文本敏感词检查。
     *
     * @param message  消息
     * @param appKey   租户
     * @param policy   策略
     * @param registry 词库注册表
     * @return 检查结果
     */
    private static ContentSafetyResult checkPlainText(Message message, String appKey,
                                                      ContentSafetyPolicy policy,
                                                      ContentSafetyRegistry registry) {
        if (!policy.isTextEnabled()) {
            return ContentSafetyResult.pass();
        }
        String text = message.getContent();
        if (StringUtils.isBlank(text)) {
            return ContentSafetyResult.pass();
        }
        SensitiveWordAcAutomaton matcher = registry.matcher(appKey);
        List<String> hits = matcher.findAll(text);
        if (hits.isEmpty()) {
            return ContentSafetyResult.pass();
        }
        return applyTextAction(message, null, text, hits, policy, matcher);
    }

    /**
     * 图文消息中的说明文字敏感词检查。
     *
     * @param message  消息
     * @param appKey   租户
     * @param policy   策略
     * @param registry 词库注册表
     * @return 检查结果
     */
    private static ContentSafetyResult checkImageText(Message message, String appKey,
                                                      ContentSafetyPolicy policy,
                                                      ContentSafetyRegistry registry) {
        if (!policy.isTextEnabled()) {
            return ContentSafetyResult.pass();
        }
        ImageTextContent body;
        try {
            body = JSON.parseObject(message.getContent(), ImageTextContent.class);
        } catch (Exception e) {
            log.warn("图文内容解析失败，按原文做敏感词 packetContentType={}", message.getContentType());
            return checkPlainText(message, appKey, policy, registry);
        }
        if (body == null) {
            return checkPlainText(message, appKey, policy, registry);
        }
        if (StringUtils.isBlank(body.getText())) {
            return ContentSafetyResult.pass();
        }
        SensitiveWordAcAutomaton matcher = registry.matcher(appKey);
        List<String> hits = matcher.findAll(body.getText());
        if (hits.isEmpty()) {
            return ContentSafetyResult.pass();
        }
        return applyTextAction(message, body, body.getText(), hits, policy, matcher);
    }

    /**
     * 按租户文本动作处理命中：REJECT / AUDIT_ONLY / MASK（默认）。
     *
     * @param message      消息（MASK 时原地改写 content）
     * @param imageText    图文结构，非图文为 null
     * @param originalText 待处理原文
     * @param hits         命中词
     * @param policy       策略
     * @param matcher      自动机（用于 MASK）
     * @return 检查结果
     */
    private static ContentSafetyResult applyTextAction(Message message, ImageTextContent imageText,
                                                       String originalText, List<String> hits,
                                                       ContentSafetyPolicy policy,
                                                       SensitiveWordAcAutomaton matcher) {
        ContentSafetyAction action = policy.getTextAction();
        log.info("敏感词命中 appKey={} action={} hits={}",
                message.getMetadata() == null ? null : message.getMetadata().getAppKey(),
                action, hits);
        if (action == ContentSafetyAction.REJECT) {
            return ContentSafetyResult.reject(ContentSafetyHitType.KEYWORD, hits,
                    ContentSafetyReasonEnum.SENSITIVE_REJECT.getCode());
        }
        if (action == ContentSafetyAction.AUDIT_ONLY) {
            return ContentSafetyResult.passAuditOnly(hits);
        }
        // 默认 MASK
        String masked = matcher.mask(originalText, hits, policy.getTextMaskChar());
        if (imageText != null) {
            imageText.setText(masked);
            message.setContent(JSON.toJSONString(imageText));
        } else {
            message.setContent(masked);
        }
        return ContentSafetyResult.passMasked(masked, hits);
    }
}
