package com.ouyunc.message.listener;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.base.constant.MqConstant;
import com.ouyunc.base.constant.enums.EventRingEnum;
import com.ouyunc.base.constant.enums.EventType;
import com.ouyunc.base.constant.enums.ExceptionSeverity;
import com.ouyunc.base.constant.enums.MessageEventTypeEnum;
import com.ouyunc.base.model.ExceptionRecord;
import com.ouyunc.core.context.MessageContext;
import com.ouyunc.core.exception.ExceptionReporter;
import com.ouyunc.core.listener.EventListener;
import com.ouyunc.core.listener.MessageEventListener;
import com.ouyunc.core.listener.event.MessageEvent;
import com.ouyunc.core.listener.event.payload.ExceptionEventPayload;
import com.ouyunc.core.properties.MessageProperties;
import com.ouyunc.mq.core.MqFactory;
import com.ouyunc.mq.core.api.MqPublisher;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 异常事件：BUSINESS 只日志；SYSTEM/PIPELINE 发故障 MQ；MQ 失败转 Persist。
 */
@EventListener(ring = EventRingEnum.SLOW)
class ExceptionMessageEventListener implements MessageEventListener<MessageEvent> {
    private static final Logger log = LoggerFactory.getLogger(ExceptionMessageEventListener.class);

    private static final MqPublisher mqPublisher = MqFactory.PUBLISHER.instance();

    @Override
    public EventType type() {
        return MessageEventTypeEnum.EXCEPTION;
    }

    @Override
    public void onEvent(MessageEvent event) {
        Object raw = event.getSource();
        if (!(raw instanceof ExceptionEventPayload payload)) {
            log.error("非法异常事件 source，已丢弃: {}", raw == null ? null : raw.getClass().getName());
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug("异常事件: severity={}, code={}, scene={}, packetId={}",
                    payload.severity(), payload.code(), payload.scene(), payload.packetId());
        }
        ExceptionSeverity severity = payload.severity() != null ? payload.severity() : ExceptionSeverity.SYSTEM;
        if (severity == ExceptionSeverity.BUSINESS) {
            return;
        }
        MessageProperties properties = MessageContext.messageProperties;
        if (properties != null && !properties.isExceptionMqEnabled()) {
            log.warn("异常 MQ 已关闭，跳过发送 scene={}, code={}", payload.scene(), payload.code());
            return;
        }
        ExceptionRecord record = ExceptionReporter.toRecord(payload, event.getPublishTime());
        String body = JSON.toJSONString(record);
        mqPublisher.send(MqConstant.MQ_EXCEPTION_TOPIC, body)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        return;
                    }
                    log.error("异常消息发 MQ 失败 scene={}, code={}", payload.scene(), payload.code(), ex);
                    if (properties != null && !properties.isExceptionPersistEnabled()) {
                        return;
                    }
                    MessageContext.publishEvent(new MessageEvent(body, MessageEventTypeEnum.EXCEPTION_PERSIST), true);
                });
    }
}
