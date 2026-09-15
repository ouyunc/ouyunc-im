package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 消息进入 IM 内核的来源，用于区分长连接客户端与 HTTP 等服务端代发入口。
 * <p>存 {@link com.ouyunc.base.model.Metadata} 时直接使用本枚举，勿再存裸字符串。</p>
 */
public enum IngressSourceEnum {

    /** IM 长连接客户端入站（WebSocket / MQTT） */
    IM("im", "IM 长连接客户端"),
    /** HTTP 外部推送 */
    HTTP_PUSH("http_push", "HTTP 外部推送"),
    /** WhatsApp Webhook 入站 */
    WHATSAPP_WEBHOOK("whatsapp_webhook", "WhatsApp Webhook 入站"),
    /** Telegram Webhook 入站 */
    TELEGRAM_WEBHOOK("telegram_webhook", "Telegram Webhook 入站"),
    ;

    /** 历史/日志用短码；新代码优先比较枚举本身。 */
    private final String code;
    /** 说明。 */
    private final String description;

    IngressSourceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    /**
     * @return 短码（兼容旧日志/扩展字段）
     */
    public String getCode() {
        return code;
    }

    /**
     * @return 中文说明
     */
    public String getDescription() {
        return description;
    }

    /**
     * @param source 入口枚举
     * @return 是否 HTTP 推送
     */
    public static boolean isHttpPush(IngressSourceEnum source) {
        return HTTP_PUSH == source;
    }

    /**
     * 兼容仍传短码的调用。
     *
     * @param ingressSource 短码或枚举名
     * @return 是否 HTTP 推送
     */
    public static boolean isHttpPush(String ingressSource) {
        return isHttpPush(fromCode(ingressSource));
    }

    /**
     * @param source 入口枚举
     * @return 是否 IM 长连接
     */
    public static boolean isImIngress(IngressSourceEnum source) {
        return IM == source;
    }

    /**
     * 兼容仍传短码的调用。
     *
     * @param ingressSource 短码或枚举名
     * @return 是否 IM 长连接
     */
    public static boolean isImIngress(String ingressSource) {
        return isImIngress(fromCode(ingressSource));
    }

    /**
     * @param source 入口枚举
     * @return 是否外部 Webhook
     */
    public static boolean isExternalWebhook(IngressSourceEnum source) {
        return WHATSAPP_WEBHOOK == source || TELEGRAM_WEBHOOK == source;
    }

    /**
     * 兼容仍传短码的调用。
     *
     * @param ingressSource 短码或枚举名
     * @return 是否外部 Webhook
     */
    public static boolean isExternalWebhook(String ingressSource) {
        return isExternalWebhook(fromCode(ingressSource));
    }

    /**
     * 按短码或枚举名解析；无法识别返回 null。
     *
     * @param code 短码（im）或枚举名（IM）
     * @return 枚举或 null
     */
    public static IngressSourceEnum fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        String trimmed = code.trim();
        for (IngressSourceEnum value : values()) {
            if (value.code.equals(trimmed) || value.name().equalsIgnoreCase(trimmed)) {
                return value;
            }
        }
        return null;
    }
}
