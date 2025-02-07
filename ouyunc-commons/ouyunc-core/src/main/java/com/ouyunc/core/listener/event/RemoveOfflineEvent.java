package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 消息移除离线事件
 **/
public class RemoveOfflineEvent extends MessageEvent {


    public RemoveOfflineEvent(Object source) {
        super(source);
    }

    public RemoveOfflineEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
