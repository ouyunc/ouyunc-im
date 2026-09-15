package com.ouyunc.base.model;

import com.ouyunc.base.constant.enums.ContentSafetyAction;
import com.ouyunc.base.constant.enums.ContentSafetyHitType;

import java.util.Collections;
import java.util.List;

/**
 * 内容安全检查结果。
 */
public final class ContentSafetyResult {

    private final boolean passed;
    private final ContentSafetyAction action;
    private final ContentSafetyHitType hitType;
    private final List<String> hitWords;
    private final String maskedContent;
    private final String reason;

    private ContentSafetyResult(boolean passed, ContentSafetyAction action, ContentSafetyHitType hitType,
                                List<String> hitWords, String maskedContent, String reason) {
        this.passed = passed;
        this.action = action;
        this.hitType = hitType;
        this.hitWords = hitWords == null ? List.of() : List.copyOf(hitWords);
        this.maskedContent = maskedContent;
        this.reason = reason;
    }

    public static ContentSafetyResult pass() {
        return new ContentSafetyResult(true, ContentSafetyAction.PASS, null, List.of(), null, null);
    }

    public static ContentSafetyResult passMasked(String maskedContent, List<String> hitWords) {
        return new ContentSafetyResult(true, ContentSafetyAction.MASK, ContentSafetyHitType.KEYWORD,
                hitWords, maskedContent, "sensitive-masked");
    }

    public static ContentSafetyResult passAuditOnly(List<String> hitWords) {
        return new ContentSafetyResult(true, ContentSafetyAction.AUDIT_ONLY, ContentSafetyHitType.KEYWORD,
                hitWords, null, "sensitive-audit");
    }

    public static ContentSafetyResult reject(ContentSafetyHitType hitType, List<String> hitWords, String reason) {
        return new ContentSafetyResult(false, ContentSafetyAction.REJECT, hitType, hitWords, null, reason);
    }

    public boolean isPassed() {
        return passed;
    }

    public ContentSafetyAction getAction() {
        return action;
    }

    public ContentSafetyHitType getHitType() {
        return hitType;
    }

    public List<String> getHitWords() {
        return hitWords == null ? Collections.emptyList() : hitWords;
    }

    public String getMaskedContent() {
        return maskedContent;
    }

    public String getReason() {
        return reason;
    }
}
