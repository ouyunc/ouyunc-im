package com.ouyunc.base.constant.enums;

import org.apache.commons.lang3.StringUtils;

/**
 * 消息进入 IM 内核的来源，用于区分长连接客户端与 HTTP 等服务端代发入口。
 */
public enum IngressSourceEnum {

    /** IM 长连接客户端入站（WebSocket / MQTT） */
    IM("im", "IM 长连接客户端"),
    HTTP_PUSH("http_push", "HTTP 外部推送"),
    WHATSAPP_WEBHOOK("whatsapp_webhook", "WhatsApp Webhook 入站"),
    TELEGRAM_WEBHOOK("telegram_webhook", "Telegram Webhook 入站"),
    ;

    private final String code;
    private final String description;

    IngressSourceEnum(String code, String description) {
        this.code = code;
        this.description = description;
    }

    public String getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    public static boolean isHttpPush(String ingressSource) {
        return HTTP_PUSH.code.equals(ingressSource);
    }

    /** IM 长连接客户端入站（WS / MQTT）。 */
    public static boolean isImIngress(String ingressSource) {
        return IM.code.equals(ingressSource);
    }

    /** 外部厂商 Webhook 入站（上行）。 */
    public static boolean isExternalWebhook(String ingressSource) {
        return WHATSAPP_WEBHOOK.code.equals(ingressSource) || TELEGRAM_WEBHOOK.code.equals(ingressSource);
    }

    public static IngressSourceEnum fromCode(String code) {
        if (StringUtils.isBlank(code)) {
            return null;
        }
        for (IngressSourceEnum value : values()) {
            if (value.code.equals(code)) {
                return value;
            }
        }
        return null;
    }
}
