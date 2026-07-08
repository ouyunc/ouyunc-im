package com.ouyunc.core.listener.event.payload;

import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;

/**
 * 异常事件载体
 */
public record ExceptionEventPayload(ExceptionCodeEnum code, String errorMessage, Packet packet) {

    public static ExceptionEventPayload of(ExceptionCodeEnum code, String errorMessage, Packet packet) {
        return new ExceptionEventPayload(code, errorMessage, packet);
    }
}
