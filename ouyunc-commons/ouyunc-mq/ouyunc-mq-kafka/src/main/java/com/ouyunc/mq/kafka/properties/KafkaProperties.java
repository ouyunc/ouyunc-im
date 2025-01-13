package com.ouyunc.mq.kafka.properties;


import com.ouyunc.mq.kafka.enums.KafkaModeEnum;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * @description: kafka 属性配置文件
 * @author fzx
 * @date 2025/1/13 16:36
 * @version 1.0
 */
public class KafkaProperties {

    /**
     * kafka 默认模式 单实例
     */
    private KafkaModeEnum model = KafkaModeEnum.STANDALONE;

    /**
     * common的连接
     **/
    private List<String> bootstrapServers = new ArrayList(Collections.singletonList("localhost:9092"));

    public KafkaModeEnum getModel() {
        return model;
    }

    public void setModel(KafkaModeEnum model) {
        this.model = model;
    }

    public List<String> getBootstrapServers() {
        return bootstrapServers;
    }

    public void setBootstrapServers(List<String> bootstrapServers) {
        this.bootstrapServers = bootstrapServers;
    }
}
