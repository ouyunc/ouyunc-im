package com.ouyunc.core.listener.event;

import com.ouyunc.base.packet.Packet;

/**
 * @Author fzx
 * @Description: 异常事件
 **/
public class ExceptionEvent extends MessageEvent {
    /**
     * packet
     */
    private final Packet packet;

    /**
     * error message
     */
    private String errorMessage;

    public ExceptionEvent(Object source,  Packet packet) {
        super(source);
        this.packet = packet;
    }

    public ExceptionEvent(Object source, String errorMessage, Packet packet) {
        super(source);
        this.packet = packet;
        this.errorMessage = errorMessage;
    }

    public ExceptionEvent(Object source, String errorMessage,  Packet packet, long publishTime) {
        super(source, publishTime);
        this.packet = packet;
        this.errorMessage = errorMessage;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Packet getPacket() {
        return packet;
    }
}
