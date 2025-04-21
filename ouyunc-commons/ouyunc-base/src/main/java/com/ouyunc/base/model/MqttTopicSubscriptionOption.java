package com.ouyunc.base.model;

import io.netty.handler.codec.mqtt.MqttQoS;
import io.netty.handler.codec.mqtt.MqttSubscriptionOption;

import java.io.Serial;
import java.io.Serializable;

/**
 * mqtt 主题订阅操作
 */
public class MqttTopicSubscriptionOption implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String topicFilter;
    private MqttQoS qos;
    private boolean noLocal;
    private boolean retainAsPublished;
    private MqttSubscriptionOption.RetainedHandlingPolicy retainHandling;

    public MqttTopicSubscriptionOption() {
    }

    public MqttTopicSubscriptionOption(String topicFilter, MqttQoS qos, boolean noLocal, boolean retainAsPublished, MqttSubscriptionOption.RetainedHandlingPolicy retainHandling) {
        this.topicFilter = topicFilter;
        this.qos = qos;
        this.noLocal = noLocal;
        this.retainAsPublished = retainAsPublished;
        this.retainHandling = retainHandling;
    }

    public String getTopicFilter() {
        return topicFilter;
    }

    public void setTopicFilter(String topicFilter) {
        this.topicFilter = topicFilter;
    }

    public MqttQoS getQos() {
        return qos;
    }

    public void setQos(MqttQoS qos) {
        this.qos = qos;
    }

    public boolean isNoLocal() {
        return noLocal;
    }

    public void setNoLocal(boolean noLocal) {
        this.noLocal = noLocal;
    }

    public boolean isRetainAsPublished() {
        return retainAsPublished;
    }

    public void setRetainAsPublished(boolean retainAsPublished) {
        this.retainAsPublished = retainAsPublished;
    }

    public MqttSubscriptionOption.RetainedHandlingPolicy getRetainHandling() {
        return retainHandling;
    }

    public void setRetainHandling(MqttSubscriptionOption.RetainedHandlingPolicy retainHandling) {
        this.retainHandling = retainHandling;
    }
}
