package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ServerOfflineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 集群中服务离线事件
 */
public class ServerOfflineEventListener implements MessageListener<ServerOfflineEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerOfflineEventListener.class);

    @Override
    public void onApplicationEvent(ServerOfflineEvent event) {
        log.error("集群中服务离线事件监听器正在处理：{}", event);
    }
}
