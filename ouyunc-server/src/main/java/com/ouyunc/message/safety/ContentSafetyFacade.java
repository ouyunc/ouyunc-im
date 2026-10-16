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
 * <p>文本先走本地词库。未直接拒绝时，按 text-vendor.provider 选择厂商。
 * 腾讯云已接入。阿里云、数美已占位，配上后暂缓受理，不会放行。
 * 超时和故障抛出不可用，由入口返回 40012。
 * 图片视频仅打 metadata 标记，监黄闭环见 P1。生产日志只打命中信息，不打原文全文。</p>
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
     * HTTP 与长连接共用检查异常策略。策略本身不可读取时暂缓受理，不能把检查故障当成检查通过。
     * 降级结果记录于日志，不记录消息原文；与内容命中 REJECT 明确区分。
     */
    public static boolean allowOnFailure(Packet packet, Exception failure) {
        if (ContentSafetyUnavailableException.causedBy(failure)) {
            Long packetId = packet == null ? null : packet.getPacketId();
            String messageId = packet == null || packet.getMessage() == null ? null : packet.getMessage().getId();
            log.error("文本审核服务不可用，暂缓受理 packetId={} messageId={}", packetId, messageId);
            return false;
        }
        boolean failOpen = false;
        try {
            String appKey = packet.getMessage().getMetadata().getIngress().getAppKey();
            failOpen = ContentSafetyRegistry.getInstance().policy(appKey).isIngressFailOpen();
        } catch (RuntimeException policyFailure) {
            log.warn("内容安全异常策略不可用，暂缓受理 packetId={}", packet.getPacketId(), policyFailure);
        }
        log.error("内容安全检查异常 packetId={} messageId={} failOpen={}",
                packet.getPacketId(), packet.getMessage().getId(), failOpen, failure);
        return failOpen;
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
        int contentType = packet.getMessage().getContentType();
        // 已读、撤回等控制命令无需审核正文，不应被词库冷启动或审核故障阻断。
        if (contentType != MessageContentTypeEnum.TEXT_CONTENT.getType()
                && contentType != MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType()
                && contentType != MessageContentTypeEnum.IMAGE_CONTENT.getType()
                && contentType != MessageContentTypeEnum.VIDEO_CONTENT.getType()) {
            return ContentSafetyResult.pass();
        }
        ContentSafetyRegistry registry = ContentSafetyRegistry.getInstance();
        if (!registry.isEnabled()) {
            return ContentSafetyResult.pass();
        }
        Message message = packet.getMessage();
        Metadata metadata = message.getMetadata();
        String appKey = metadata == null ? null : metadata.getIngress().getAppKey();
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
            return checkPlainText(packet, appKey, policy, registry);
        }
        if (contentType == MessageContentTypeEnum.IMAGE_TEXT_CONTENT.getType()) {
            return checkImageText(packet, appKey, policy, registry);
        }

        // 媒体：P0 放行，写标记供 P1 监黄使用
        if (policy.isMediaEnabled()
                && (contentType == MessageContentTypeEnum.IMAGE_CONTENT.getType()
                || contentType == MessageContentTypeEnum.VIDEO_CONTENT.getType())) {
            if (metadata != null && metadata.getIngress().getModerationStatus() == null) {
                metadata.ensureIngress().setModerationStatus(ModerationStatusEnum.NONE);
                metadata.ensureIngress().setModerationMode(ModerationModeEnum.fromAction(
                        policy.getMediaAction(), ModerationModeEnum.SEND_THEN_REVIEW));
            }
        }
        return ContentSafetyResult.pass();
    }

    /**
     * 纯文本敏感词检查。
     *
     * @param packet   协议包，正文和 packetId 都从包上取
     * @param appKey   租户
     * @param policy   策略
     * @param registry 词库注册表
     * @return 检查结果
     */
    private static ContentSafetyResult checkPlainText(Packet packet, String appKey,
                                                      ContentSafetyPolicy policy,
                                                      ContentSafetyRegistry registry) {
        if (!policy.isTextEnabled()) {
            return ContentSafetyResult.pass();
        }
        Message message = packet.getMessage();
        String text = message.getContent();
        if (StringUtils.isBlank(text)) {
            return ContentSafetyResult.pass();
        }
        SensitiveWordAcAutomaton matcher = registry.matcher(appKey);
        List<String> hits = matcher.findAll(text);
        return finishTextCheck(packet, null, text, hits, policy, matcher);
    }

    /**
     * 图文消息中的说明文字敏感词检查。
     *
     * @param packet   协议包，正文和 packetId 都从包上取
     * @param appKey   租户
     * @param policy   策略
     * @param registry 词库注册表
     * @return 检查结果
     */
    private static ContentSafetyResult checkImageText(Packet packet, String appKey,
                                                      ContentSafetyPolicy policy,
                                                      ContentSafetyRegistry registry) {
        if (!policy.isTextEnabled()) {
            return ContentSafetyResult.pass();
        }
        Message message = packet.getMessage();
        ImageTextContent body;
        try {
            body = JSON.parseObject(message.getContent(), ImageTextContent.class);
        } catch (Exception e) {
            log.warn("图文内容解析失败，按原文做敏感词 packetContentType={}", message.getContentType());
            return checkPlainText(packet, appKey, policy, registry);
        }
        if (body == null) {
            return checkPlainText(packet, appKey, policy, registry);
        }
        if (StringUtils.isBlank(body.getText())) {
            return ContentSafetyResult.pass();
        }
        SensitiveWordAcAutomaton matcher = registry.matcher(appKey);
        List<String> hits = matcher.findAll(body.getText());
        return finishTextCheck(packet, body, body.getText(), hits, policy, matcher);
    }

    /**
     * 本地词库若已是拒绝，不再请求第三方。否则先做腾讯文本审核，通过后再按租户策略打码或放行。
     *
     * @param packet    协议包，正文和 packetId 都从包上取
     * @param imageText 图文结构，纯文本为 null
     * @param text      待审核原文
     * @param hits      本地命中词
     * @param policy    策略
     * @param matcher   自动机
     * @return 检查结果；第三方不可用时抛出 {@link ContentSafetyUnavailableException}
     */
    private static ContentSafetyResult finishTextCheck(Packet packet, ImageTextContent imageText, String text,
                                                        List<String> hits, ContentSafetyPolicy policy,
                                                        SensitiveWordAcAutomaton matcher) {
        if (!hits.isEmpty() && policy.getTextAction() == ContentSafetyAction.REJECT) {
            return applyTextAction(packet, imageText, text, hits, policy, matcher);
        }
        ContentSafetyResult vendor = TextModerationStrategies.moderate(text, String.valueOf(packet.getPacketId()));
        if (vendor != null) {
            return vendor;
        }
        if (hits.isEmpty()) {
            return ContentSafetyResult.pass();
        }
        return applyTextAction(packet, imageText, text, hits, policy, matcher);
    }

    /**
     * 按租户文本动作处理命中：REJECT / AUDIT_ONLY / MASK（默认）。
     *
     * @param packet       协议包（MASK 时原地改写 message.content）
     * @param imageText    图文结构，非图文为 null
     * @param originalText 待处理原文
     * @param hits         命中词
     * @param policy       策略
     * @param matcher      自动机（用于 MASK）
     * @return 检查结果
     */
    private static ContentSafetyResult applyTextAction(Packet packet, ImageTextContent imageText,
                                                       String originalText, List<String> hits,
                                                       ContentSafetyPolicy policy,
                                                       SensitiveWordAcAutomaton matcher) {
        Message message = packet.getMessage();
        ContentSafetyAction action = policy.getTextAction();
        log.info("敏感词命中 appKey={} action={} hits={}",
                message.getMetadata() == null ? null : message.getMetadata().getIngress().getAppKey(),
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
