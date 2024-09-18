package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 消息发送失败事件
 **/
public class SendFailEvent extends MessageEvent {


    public SendFailEvent(Object source) {
        super(source);
    }

    public SendFailEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
