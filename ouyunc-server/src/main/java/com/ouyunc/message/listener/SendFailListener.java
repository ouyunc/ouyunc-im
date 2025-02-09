package com.ouyunc.message.listener;

import com.ouyunc.base.model.SendResult;
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
     * kafkaTemplate
     */
    //private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();


    /**
     * @Author fzx
     * @Description 处理发送消息失败的事件
     */
    @Override
    public void onApplicationEvent(SendFailEvent event) {
        if (log.isDebugEnabled()) {
            log.error("消息发送失败事件监听器正在处理：{}", event.getSource());
        }
        // 这里可以丢到mq中去处理，注意：发送失败的消息可能是重试的或者集群间消息传递，所以可能业务上需要做幂等处理
        if (event.getSource() instanceof SendResult sendResult) {
            //kafkaTemplate.send("ouyunc-message-send-fail", sendResult);
        }
    }
}
