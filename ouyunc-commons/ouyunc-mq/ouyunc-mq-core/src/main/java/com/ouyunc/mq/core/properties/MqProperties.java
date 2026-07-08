package com.ouyunc.mq.core.properties;

/**
 * MQ 顶层配置，对应 {@code ouyunc.mq} 前缀。
 */
public class MqProperties {

    /**
     * 消息队列类型：kafka（默认）| rocket | rocketmq
     */
    private String type = "kafka";

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }
}
