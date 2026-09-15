package com.ouyunc.base.constant.enums;

/**
 * 内容安全命中原因码（审计 / {@code ContentSafetyResult#getReason()}，勿写全文）。
 */
public enum ContentSafetyReasonEnum {

    /** 敏感词已脱敏放行。 */
    SENSITIVE_MASKED("sensitive-masked"),
    /** 敏感词仅审计放行。 */
    SENSITIVE_AUDIT("sensitive-audit"),
    /** 敏感词拒绝发送。 */
    SENSITIVE_REJECT("sensitive-reject");

    private final String code;

    ContentSafetyReasonEnum(String code) {
        this.code = code;
    }

    /**
     * @return 稳定原因码字符串
     */
    public String getCode() {
        return code;
    }
}
