package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 消息发送离线事件
 **/
public class SendOfflineEvent extends MessageEvent {


    public SendOfflineEvent(Object source) {
        super(source);
    }

    public SendOfflineEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
