package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.constant.enums.ContentSafetyHitType;
import com.ouyunc.base.constant.enums.ContentSafetyReasonEnum;

import java.util.Collections;
import java.util.List;

/**
 * 内容安全一次检查的结果。
 * <p>{@link #isPassed()} 为 false 时调用方不得继续 fire / 落库；MASK 时原文已在 packet 内被改写。</p>
 */
public final class ContentSafetyResult {

    /** true 表示可继续投递（含 MASK / AUDIT_ONLY / 未命中）。 */
    private final boolean passed;
    /** 实际执行的动作。 */
    private final ContentSafetyAction action;
    /** 命中类型；未命中时为 null。 */
    private final ContentSafetyHitType hitType;
    /** 命中词列表（不可变，不含全文）。 */
    private final List<String> hitWords;
    /** MASK 后的文本；非 MASK 时为 null。 */
    private final String maskedContent;
    /** 拒绝或审计原因码，如 sensitive-reject。 */
    private final String reason;

    /**
     * 内部构造；hitWords 会拷贝为不可变列表。
     */
    private ContentSafetyResult(boolean passed, ContentSafetyAction action, ContentSafetyHitType hitType,
                                List<String> hitWords, String maskedContent, String reason) {
        this.passed = passed;
        this.action = action;
        this.hitType = hitType;
        this.hitWords = hitWords == null ? List.of() : List.copyOf(hitWords);
        this.maskedContent = maskedContent;
        this.reason = reason;
    }

    /**
     * 未命中或策略关闭时的放行结果。
     *
     * @return 可继续投递的结果
     */
    public static ContentSafetyResult pass() {
        return new ContentSafetyResult(true, ContentSafetyAction.PASS, null, List.of(), null, null);
    }

    /**
     * 敏感词脱敏后放行。
     *
     * @param maskedContent 脱敏后文本
     * @param hitWords      命中词
     * @return 可继续投递的结果
     */
    public static ContentSafetyResult passMasked(String maskedContent, List<String> hitWords) {
        return new ContentSafetyResult(true, ContentSafetyAction.MASK, ContentSafetyHitType.KEYWORD,
                hitWords, maskedContent, ContentSafetyReasonEnum.SENSITIVE_MASKED.getCode());
    }

    /**
     * 仅审计、原文放行。
     *
     * @param hitWords 命中词
     * @return 可继续投递的结果
     */
    public static ContentSafetyResult passAuditOnly(List<String> hitWords) {
        return new ContentSafetyResult(true, ContentSafetyAction.AUDIT_ONLY, ContentSafetyHitType.KEYWORD,
                hitWords, null, ContentSafetyReasonEnum.SENSITIVE_AUDIT.getCode());
    }

    /**
     * 拒绝发送。
     *
     * @param hitType  命中类型
     * @param hitWords 命中词
     * @param reason   原因码
     * @return passed=false 的结果
     */
    public static ContentSafetyResult reject(ContentSafetyHitType hitType, List<String> hitWords, String reason) {
        return new ContentSafetyResult(false, ContentSafetyAction.REJECT, hitType, hitWords, null, reason);
    }

    /**
     * @return true 可继续投递
     */
    public boolean isPassed() {
        return passed;
    }

    /**
     * @return 实际动作
     */
    public ContentSafetyAction getAction() {
        return action;
    }

    /**
     * @return 命中类型，未命中为 null
     */
    public ContentSafetyHitType getHitType() {
        return hitType;
    }

    /**
     * @return 命中词，永不返回 null
     */
    public List<String> getHitWords() {
        return hitWords == null ? Collections.emptyList() : hitWords;
    }

    /**
     * @return MASK 后文本；非 MASK 为 null
     */
    public String getMaskedContent() {
        return maskedContent;
    }

    /**
     * @return 原因码
     */
    public String getReason() {
        return reason;
    }
}
