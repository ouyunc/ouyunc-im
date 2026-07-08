package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.ExceptionCodeEnum;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.exception.MessageException;
import com.ouyunc.base.packet.Packet;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.message.context.MessageServerContext;
import com.ouyunc.mq.core.MqFactory;
import com.ouyunc.mq.core.api.MqPublisher;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @Author fzx
 * @Description: 异常消息处理监听器，原则上所有异常消息进mq来处理
 */
@EventListener(ring = EventRingEnum.SLOW)
class ExceptionMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ExceptionMessageEventListener.class);

    private static final MqPublisher mqPublisher = MqFactory.PUBLISHER.instance();

    /**
     * @Author fzx
     * @Description 异常消息处理监听器,具体需要哪些数据，后面根据业务在提取
     * @Param [event]
     */
    @Override
    public EventType type() {
        return MessageEventTypeEnum.EXCEPTION;
    }

    @Override
    public void onEvent(MessageEvent event) {
        if (log.isDebugEnabled()) {
            log.error("异常事件监听器正在处理：{}", JSON.toJSONString(event.getSource()));
        }
        Object raw = event.getSource();
        ExceptionCodeEnum code;
        String errorMessage;
        Packet packet;
        long publishTime = event.getPublishTime();

        switch (raw) {
            case Throwable throwable -> {
                code = ExceptionCodeEnum.UNKNOWN_ERROR;
                errorMessage = extractExceptionInfo(throwable);
                if (StringUtils.isBlank(errorMessage)) {
                    errorMessage = ExceptionCodeEnum.UNKNOWN_ERROR.getMessage();
                }
                packet = null;
            }
            case ExceptionEventPayload(ExceptionCodeEnum code1, String message, Packet packet1) -> {
                code = code1 != null ? code1 : ExceptionCodeEnum.UNKNOWN_ERROR;
                errorMessage = message;
                packet = packet1;
                if (StringUtils.isBlank(errorMessage)) {
                    errorMessage = code.getMessage();
                }
            }
            case ExceptionCodeEnum exceptionCode -> {
                code = exceptionCode;
                errorMessage = exceptionCode.getMessage();
                packet = null;
            }
            case null, default -> {
                code = ExceptionCodeEnum.UNKNOWN_ERROR;
                errorMessage = ExceptionCodeEnum.UNKNOWN_ERROR.getMessage();
                packet = null;
            }
        }

        mqPublisher.send(MqConstant.MQ_EXCEPTION_TOPIC,
                JSON.toJSONString(new MessageException(errorMessage, code, packet, publishTime)))
                .whenComplete((result, ex) -> {
                    if (ex != null) {
                        log.error("处理异常消息异常，原因：{}", ex.getMessage());
                        MessageServerContext.publishEvent(new MessageEvent(event, MessageEventTypeEnum.EXCEPTION_PERSIST), true);
                    }
                });

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
