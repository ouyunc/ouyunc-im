package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.RemoveOfflineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常离线消息监听器
 */
public class RemoveOfflineListener implements MessageListener<RemoveOfflineEvent> {

    private static final Logger log = LoggerFactory.getLogger(RemoveOfflineListener.class);


    @Override
    public void onApplicationEvent(RemoveOfflineEvent event) {
        log.debug("移除离线消息监听器正在处理：{}", event.getSource());
    }
}
