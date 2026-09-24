package com.ouyunc.message.helper;

/**
 * 外部渠道 Kafka 在确认超时或失败时抛出。调用方不得把这次投递回成受理成功。
 */
public final class ExternalDeliveryConfirmException extends IllegalStateException {

    public ExternalDeliveryConfirmException(String message, Throwable cause) {
        super(message, cause);
    }
}
