package com.ouyunc.mq.kafka;

import com.ouyunc.mq.kafka.builder.AbstractKafkaBuilder;
import com.ouyunc.mq.kafka.builder.KafkaAdminBuilder;
import com.ouyunc.mq.kafka.builder.KafkaTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaAdmin;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * @author fzx
 * @version 1.0
 * @description: kafka 工厂
 */
public enum KafkaFactory {

    KAFKA_TEMPLATE(3, "kafkaTemplate v3 版本") {

        private static volatile KafkaTemplate<?, ?> kafkaTemplate;


        @SuppressWarnings("unchecked")
        @Override
        public KafkaTemplate<?,?> instance() {
            if (kafkaTemplate == null) {
                synchronized (KafkaFactory.class) {
                    if (kafkaTemplate == null) {
                        AbstractKafkaBuilder<KafkaTemplate<?,?>> kafkaTemplateBuilder = new KafkaTemplateBuilder();
                        kafkaTemplate =  kafkaTemplateBuilder.build();
                    }
                }
            }
            return kafkaTemplate;
        }
    },

    KAFKA_ADMIN_CLIENT(3, "kafkaAdminClient v3 版本") {
        private static volatile KafkaAdmin kafkaAdmin;

        @SuppressWarnings("unchecked")
        @Override
        public KafkaAdmin instance() {
            if (kafkaAdmin == null) {
                synchronized (KafkaFactory.class) {
                    if (kafkaAdmin == null) {
                        AbstractKafkaBuilder<KafkaAdmin> kafkaAdminClientBuilder = new KafkaAdminBuilder();
                        kafkaAdmin =  kafkaAdminClientBuilder.build();
                    }
                }
            }
            return kafkaAdmin;
        }
    }
    ;

    private final int version;

    private final String description;

    KafkaFactory(int version, String description) {
        this.version = version;
        this.description = description;
    }

    public int getVersion() {
        return version;
    }



    public String getDescription() {
        return description;
    }


    private static final Logger log = LoggerFactory.getLogger(KafkaFactory.class);



    /**
     * @Author fzx
     * @Description 获取实例
     */
    public abstract <T> T instance();
}
