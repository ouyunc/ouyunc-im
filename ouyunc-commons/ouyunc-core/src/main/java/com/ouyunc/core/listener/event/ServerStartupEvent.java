package com.ouyunc.core.listener.event;

/**
 * @Author fzx
 * @Description: 服务启动成功事件
 **/
public class ServerStartupEvent extends MessageEvent {


    public ServerStartupEvent(Object source) {
        super(source);
    }

    public ServerStartupEvent(Object source, long publishTime) {
        super(source, publishTime);
    }

}
