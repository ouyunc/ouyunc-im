package com.ouyunc.message.processor.http.push.delivery;

/**
 * HTTP 推送投递策略注册表。
 */
public final class HttpPushProcessorStrategies {

    private static final HttpProcessor[] BY_MESSAGE_TYPE = new HttpProcessor[256];

    static {
        register(One2OneHttpPushDeliveryStrategy.INSTANCE);
        register(GroupHttpPushDeliveryStrategy.INSTANCE);
        register(ServerNotifyHttpPushDeliveryStrategy.INSTANCE);
        register(CsHttpPushDeliveryStrategy.INSTANCE);
    }

    private HttpPushProcessorStrategies() {
    }

    private static void register(HttpProcessor strategy) {
        BY_MESSAGE_TYPE[strategy.messageType().getType() & 0xFF] = strategy;
    }

    public static HttpProcessor get(byte messageType) {
        return BY_MESSAGE_TYPE[messageType & 0xFF];
    }
}
