package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ClientLoginEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 客户端登录成功事件监听器
 **/
public class ClientLoginListener implements MessageListener<ClientLoginEvent> {

    private static final Logger log = LoggerFactory.getLogger(ClientLoginListener.class);


    /**
     * @Author fzx
     * @Description 处理客户端上线事件/成功登录的时候会触发
     */
    @Override
    public void onApplicationEvent(ClientLoginEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("客户端上线事件监听器正在处理：{}", event);
        }
    }
}
