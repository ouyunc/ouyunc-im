package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 离线事件
 **/
public class ServerOfflineEvent extends MessageEvent {


    public ServerOfflineEvent(Object source) {
        super(source);
    }

    public ServerOfflineEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
