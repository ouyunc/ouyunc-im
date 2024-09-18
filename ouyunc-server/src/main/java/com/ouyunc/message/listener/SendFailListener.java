package com.ouyunc.message.listener;

import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.SendFailEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息发送失败监听器， 可以做消息日志的记录，重发等操作
 **/
public class SendFailListener implements MessageListener<SendFailEvent> {
    private static final Logger log = LoggerFactory.getLogger(SendFailListener.class);


    /**
     * @Author fzx
     * @Description 处理发送消息失败的事件
     */
    @Override
    public void onApplicationEvent(SendFailEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("消息发送失败事件监听器正在处理：{}", event);
        }
        // do nothing
    }
}
