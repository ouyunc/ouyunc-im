package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 保存消息事件
 **/
public class SaveMessageEvent extends MessageEvent {


    public SaveMessageEvent(Object source) {
        super(source);
    }

    public SaveMessageEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
