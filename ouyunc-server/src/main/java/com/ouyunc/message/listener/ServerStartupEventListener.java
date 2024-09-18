package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStartupEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 服务启动成功事件
 */
public class ServerStartupEventListener implements MessageListener<ServerStartupEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStartupEventListener.class);

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
    }
}
