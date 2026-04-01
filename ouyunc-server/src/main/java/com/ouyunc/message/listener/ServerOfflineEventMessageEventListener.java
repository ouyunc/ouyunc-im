package com.ouyunc.message.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author fzx
 * @description 集群中服务离线事件
 */
@EventListener
class ServerOfflineEventMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerOfflineEventMessageEventListener.class);

    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_OFFLINE;
    }

    @Override
    public void onEvent(MessageEvent event) {
        log.error("集群中服务离线事件监听器正在处理：{}", event);
    }
}
