package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 客户端离线/注销事件
 **/
public class ClientLogoutEvent extends MessageEvent {


    public ClientLogoutEvent(Object source) {
        super(source);
    }

    public ClientLogoutEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
