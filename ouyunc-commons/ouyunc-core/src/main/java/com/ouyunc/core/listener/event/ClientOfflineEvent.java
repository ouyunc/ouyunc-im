package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 客户端离线事件
 **/
public class ClientOfflineEvent extends MessageEvent {


    public ClientOfflineEvent(Object source) {
        super(source);
    }

    public ClientOfflineEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
