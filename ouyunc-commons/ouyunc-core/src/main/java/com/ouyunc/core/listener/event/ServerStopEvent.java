package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 服务启动成功事件
 **/
public class ServerStopEvent extends MessageEvent {


    public ServerStopEvent(Object source) {
        super(source);
    }

    public ServerStopEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
