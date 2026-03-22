package com.ouyunc.core.listener.event;

/**
 * Kafka 发送失败后的本地持久化请求；由专用监听器落库，不再进入发 Kafka 的异常监听器，避免循环重试。
 */
public class ExceptionPersistEvent extends MessageEvent {

    public ExceptionPersistEvent(ExceptionEvent failedKafkaExceptionEvent) {
        super(failedKafkaExceptionEvent);
    }

    public ExceptionEvent getExceptionPayload() {
        return (ExceptionEvent) getSource();
    }
}
