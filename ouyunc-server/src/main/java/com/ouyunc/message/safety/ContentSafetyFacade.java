package com.ouyunc.message.safety;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.constant.enums.ContentSafetyHitType;
import com.ouyunc.base.constant.enums.MessageContentTypeEnum;
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
 * 内容安全统一入口（WS / MQTT / HTTP Push）。
 * <p>P0：文本敏感词 MASK/REJECT/AUDIT_ONLY；媒体仅打日志标记，监黄闭环见 P1。</p>
 */
public final class ContentSafetyFacade {

    private static final Logger log = LoggerFactory.getLogger(ContentSafetyFacade.class);

    private ContentSafetyFacade() {
    }

    /**
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
        if (StringUtils.isBlank(appKey)) {
            return ContentSafetyResult.pass();
        }
        ContentSafetyPolicy policy = registry.policy(appKey);
        int contentType = message.getContentType();

        // 文本 / 图文说明
        if (contentType == MessageContentTypeEnum.TEXT_CONTENT.getType()) {
            return checkPlainText(message, appKey, policy, registry);
        }
        if (contentType == MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType()) {
            return checkImageText(message, appKey, policy, registry);
        }

        // 媒体：P0 放行，写 PENDING 标记供 P1 监黄使用
        if (policy.isMediaEnabled()
                && (contentType == MessageContentTypeEnum.IMAGE_CONTENT.getType()
                || contentType == MessageContentTypeEnum.VIDEO_CONTENT.getType())) {
            if (metadata != null && StringUtils.isBlank(metadata.getModerationStatus())) {
                metadata.setModerationStatus("NONE");
                metadata.setModerationMode(policy.getMediaAction().name());
            }
        }
        return ContentSafetyResult.pass();
    }

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
            log.warn("图文内容解析失败，跳过敏感词 packetContentType={}", message.getContentType());
            return ContentSafetyResult.pass();
        }
        if (body == null || StringUtils.isBlank(body.getText())) {
            return ContentSafetyResult.pass();
        }
        SensitiveWordAcAutomaton matcher = registry.matcher(appKey);
        List<String> hits = matcher.findAll(body.getText());
        if (hits.isEmpty()) {
            return ContentSafetyResult.pass();
        }
        return applyTextAction(message, body, body.getText(), hits, policy, matcher);
    }

    private static ContentSafetyResult applyTextAction(Message message, ImageTextContent imageText,
                                                       String originalText, List<String> hits,
                                                       ContentSafetyPolicy policy,
                                                       SensitiveWordAcAutomaton matcher) {
        ContentSafetyAction action = policy.getTextAction();
        log.info("敏感词命中 appKey={} action={} hits={}",
                message.getMetadata() == null ? null : message.getMetadata().getAppKey(),
                action, hits);
        if (action == ContentSafetyAction.REJECT) {
            return ContentSafetyResult.reject(ContentSafetyHitType.KEYWORD, hits, "sensitive-reject");
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
