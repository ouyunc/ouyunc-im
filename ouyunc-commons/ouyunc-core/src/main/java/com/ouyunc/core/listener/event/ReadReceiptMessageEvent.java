package com.ouyunc.core.listener.event;


/**
 * 读一回执消息事件
 */
public class ReadReceiptMessageEvent extends MessageEvent{
    public ReadReceiptMessageEvent(Object source) {
        super(source);
    }

    public ReadReceiptMessageEvent(Object source, long publishTime) {
        super(source, publishTime);
    }
}
