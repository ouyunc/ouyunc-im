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
 * @description 服务注销事件
 */
@EventListener
class ServerStopEventMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ServerStopEventMessageEventListener.class);

    /**
     * 服务注销事件,
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.SERVER_STOP;
    }

    @Override
    public void onEvent(MessageEvent event) {
        log.info("服务:{} 正在注销...", event);
        // 发送邮件？mq?
    }

}
