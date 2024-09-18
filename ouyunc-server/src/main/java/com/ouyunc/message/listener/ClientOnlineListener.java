package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ClientOnlineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 上线事件监听器
 **/
public class ClientOnlineListener implements MessageListener<ClientOnlineEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientOnlineListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端上线事件，登录的时候会触发
     */
    @Override
    public void onApplicationEvent(ClientOnlineEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("客户端上线事件监听器正在处理：{}", event);
        }
        // do nothing
    }
}
