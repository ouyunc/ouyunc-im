package com.ouyunc.mq.kafka;

import com.ouyunc.base.config.ConfigBinder;
import com.ouyunc.mq.kafka.builder.AbstractKafkaBuilder;

/**
 * 把 Kafka 配置挂到启动入口，构建器只读取绑定结果。
 */
public final class KafkaConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        AbstractKafkaBuilder.bind();
    }
}
