package com.ouyunc.message.listener;

import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.SendOfflineEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 消息发送离线事件监听器，将离线消息发送到mq来处理
 **/
public class SendOfflineListener implements MessageListener<SendOfflineEvent> {
    private static final Logger log = LoggerFactory.getLogger(SendOfflineListener.class);

    /**
     * kafkaTemplate
     */
    //private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();


    /**
     * @Author fzx
     * @Description 处理发送消息失败的事件
     */
    @Override
    public void onApplicationEvent(SendOfflineEvent event) {
        if (log.isDebugEnabled()) {
            log.debug("离线消息发送事件监听器正在处理：{}", event);
        }
        // 这里可以丢到mq中去处理，注意：发送失败的消息可能是重试的或者集群间消息传递，所以可能业务上需要做幂等处理
        if (event.getSource() instanceof Packet packet) {

        }
    }
}
