package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * @Author fzx
 * @Description: 异常消息处理监听器，原则上所有异常消息进mq来处理
 **/
public class ExceptionListener implements MessageListener<ExceptionEvent> {
    private static final Logger log = LoggerFactory.getLogger(ExceptionListener.class);

    /**
     * kafkaTemplate
     */
    private static final KafkaTemplate<String, Object> kafkaTemplate = KafkaFactory.KAFKA_TEMPLATE.instance();


    /**
     * @Author fzx
     * @Description 异常消息处理监听器
     * @Param [event]
     */
    @Override
    public void onApplicationEvent(ExceptionEvent event) {
        if (log.isDebugEnabled()) {
            log.error("异常事件监听器正在处理：{}", JSON.toJSONString(event.getSource()));
        }
        Object source = event.getSource();
        Packet packet = event.getPacket();
        if (source instanceof ExceptionCodeEnum exceptionCode) {

        }else if (source instanceof Throwable throwable){

        }else {

        }
        // @todo 发送到kafka
        //kafkaTemplate.send("ouyunc-message-exception", event.getSource());
    }
}
