package com.ouyunc.core.listener.event;


/**
 * 撤销消息事件
 */
public class WithdrawMessageEvent extends MessageEvent{
    public WithdrawMessageEvent(Object source) {
        super(source);
    }

    public WithdrawMessageEvent(Object source, long publishTime) {
        super(source, publishTime);
    }
}
