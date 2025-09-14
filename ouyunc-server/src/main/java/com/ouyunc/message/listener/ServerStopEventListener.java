package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerStopEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 服务注销事件
 */
public class ServerStopEventListener implements MessageListener<ServerStopEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStopEventListener.class);

    /**
     * 服务注销事件,
     * @param event
     */
    @Override
    public void onApplicationEvent(ServerStopEvent event) {
        log.info("服务:{} 正在注销...", event);
        // 发送邮件？mq?
    }

}
