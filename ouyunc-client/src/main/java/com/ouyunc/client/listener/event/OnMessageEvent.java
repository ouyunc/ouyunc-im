package com.ouyunc.client.listener.event;

import com.ouyunc.core.listener.event.MessageEvent;

/**
 * @Author fzx
 * @Description: 接收消息事件
 **/
public class OnMessageEvent extends MessageEvent {


    public OnMessageEvent(Object source) {
        super(source);
    }

    public OnMessageEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
