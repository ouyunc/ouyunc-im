package com.ouyunc.core.exception;

/**
 * 同一正式 packet 的首次扇出已有 owner。调用方应返回未知，让客户端稍后重试，不能再开一路广播。
 */
public final class DeliveryRunBusyException extends IllegalStateException {

    public DeliveryRunBusyException(String message) {
        super(message);
    }
}
