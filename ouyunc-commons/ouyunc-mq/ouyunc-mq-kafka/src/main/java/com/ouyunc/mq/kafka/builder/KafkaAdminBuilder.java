package com.ouyunc.mq.kafka.builder;

import org.apache.kafka.clients.admin.AdminClientConfig;
import org.springframework.kafka.core.KafkaAdmin;

import java.util.HashMap;
import java.util.Map;

/**
 * @author fzx
 * @version 1.0
 * @description: kafka admin 构建者
 * @date 2025/1/13 16:48
 */
public class KafkaAdminBuilder extends AbstractKafkaBuilder<KafkaAdmin>{

    /**
     * @description: 构建kafka admin
     * @author fzx
     * @date 2025/1/13 16:51
     * @version 1.0
     */
    @Override
    public KafkaAdmin build() {
        Map<String, Object> props = new HashMap<>(1);
        //配置Kafka实例的连接地址
        props.put(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG, kafkaProperties.getBootstrapServers());
        return new KafkaAdmin(props);
    }
}
