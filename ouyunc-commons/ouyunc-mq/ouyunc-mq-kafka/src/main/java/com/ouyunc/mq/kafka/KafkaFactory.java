package com.ouyunc.mq.kafka;

import com.ouyunc.mq.kafka.builder.AbstractKafkaBuilder;
import com.ouyunc.mq.kafka.builder.KafkaAdminBuilder;
import com.ouyunc.mq.kafka.builder.KafkaListenerContainerFactoryBuilder;
import com.ouyunc.mq.kafka.builder.KafkaTemplateBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.config.KafkaListenerContainerFactory;
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
                        return kafkaTemplate =  kafkaTemplateBuilder.build();
                    }
                }
            }
            return kafkaTemplate;
        }
    },

    KAFKA_LISTENER_CONTAINER(3, "kafkaListenerContainerFactory v3 版本") {

        // kafka 监听容器工厂,在使用创建容器的时候，关闭应用记得停止容器和销毁容器
        private static volatile KafkaListenerContainerFactory<?> kafkaListenerContainerFactory;

        @SuppressWarnings("unchecked")
        @Override
        public KafkaListenerContainerFactory<?> instance() {
            if (kafkaListenerContainerFactory == null) {
                synchronized (KafkaFactory.class) {
                    if (kafkaListenerContainerFactory == null) {
                        AbstractKafkaBuilder<KafkaListenerContainerFactory<?>> kafkaTemplateBuilder = new KafkaListenerContainerFactoryBuilder();
                        return kafkaListenerContainerFactory =  kafkaTemplateBuilder.build();
                    }
                }
            }
            return kafkaListenerContainerFactory;
        }



    },

    KAFKA_ADMIN(3, "kafkaAdmin v3 版本") {
        private static volatile KafkaAdmin kafkaAdmin;

        @SuppressWarnings("unchecked")
        @Override
        public KafkaAdmin instance() {
            if (kafkaAdmin == null) {
                synchronized (KafkaFactory.class) {
                    if (kafkaAdmin == null) {
                        AbstractKafkaBuilder<KafkaAdmin> kafkaAdminClientBuilder = new KafkaAdminBuilder();
                        return kafkaAdmin =  kafkaAdminClientBuilder.build();
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
