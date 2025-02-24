package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageListener;
import com.ouyunc.core.listener.event.ExceptionEvent;
import com.ouyunc.mq.kafka.KafkaFactory;
import org.apache.commons.lang3.StringUtils;
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
        String errorMessage = event.getErrorMessage();
        // packet 可能为空
        Packet packet = event.getPacket();
        long publishTime = event.getPublishTime();

        if (source instanceof ExceptionCodeEnum exceptionCode) {
            if (StringUtils.isBlank(errorMessage)) {
                errorMessage = exceptionCode.getMessage();
            }
        }else if (source instanceof Throwable throwable){
            source = ExceptionCodeEnum.UNKNOWN_ERROR;
            errorMessage = extractExceptionInfo(throwable);
            if (StringUtils.isBlank(errorMessage)) {
                errorMessage = ExceptionCodeEnum.UNKNOWN_ERROR.getMessage();
            }
        }else {
            source = ExceptionCodeEnum.UNKNOWN_ERROR;
            errorMessage = ExceptionCodeEnum.UNKNOWN_ERROR.getMessage();
        }
        // 发送到kafka
        kafkaTemplate.send(MqConstant.KAFKA_EXCEPTION_TOPIC, new MessageException(errorMessage, (ExceptionCodeEnum) source, packet, publishTime));
    }



    private static String extractExceptionInfo(Throwable throwable) {
        StringBuilder info = new StringBuilder();
        info.append("异常类型: ").append(throwable.getClass().getName()).append("\n");
        info.append("异常消息: ").append(throwable.getMessage()).append("\n");
        info.append("堆栈跟踪:\n");
        for (StackTraceElement element : throwable.getStackTrace()) {
            info.append("\t").append(element.toString()).append("\n");
        }
        return info.toString();
    }
}
