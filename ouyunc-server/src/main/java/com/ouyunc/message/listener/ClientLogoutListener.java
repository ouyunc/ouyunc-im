package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ClientLogoutEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 离线监听器
 **/
public class ClientLogoutListener implements MessageListener<ClientLogoutEvent> {
    private static final Logger log = LoggerFactory.getLogger(ClientLogoutListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端离线事件，比如发送离线预警到mq
     */
    @Override
    public void onApplicationEvent(ClientLogoutEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("离线事件监听器正在处理：{}", event);
        }
        // do nothing
    }
}
