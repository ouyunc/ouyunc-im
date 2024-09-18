package com.ouyunc.client.listener;

import com.ouyunc.client.listener.event.OnMessageEvent;
import com.ouyunc.core.listener.MessageListener;

/**
 * @author fzx
 * @description 消息监听器
 */
public class OnMessageListener implements MessageListener<OnMessageEvent> {
    @Override
    public void onApplicationEvent(OnMessageEvent event) {
        // 这里根据协议进行策略来处理
        System.out.println(event);
    }
}
