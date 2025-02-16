package com.ouyunc.core.listener.event;

import com.ouyunc.base.packet.Packet;

/**
 * @Author fzx
 * @Description: 异常事件
 **/
public class ExceptionEvent extends MessageEvent {
    /**
     * channel 上下文
     */
    private final Packet packet;


    public ExceptionEvent(Object source, Packet packet) {
        super(source);
        this.packet = packet;
    }

    public ExceptionEvent(Object source, long publishTime, Packet packet) {
        super(source, publishTime);
        this.packet = packet;
    }

    public Packet getPacket() {
        return packet;
    }
}
