package com.ouyunc.client.listener;

import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.MessageEvent;

/**
 * @author fzx
 * @description 消息监听器
 */
public class OnMessageListener implements MessageListener<MessageEvent> {
    @Override
    public EventType type() {
        return MessageEventTypeEnum.ON_MESSAGE;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (event.getType() != MessageEventTypeEnum.ON_MESSAGE) {
            return;
        }
        // 这里根据协议进行策略来处理
        System.out.println(event);
    }
}
