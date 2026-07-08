package com.ouyunc.mq.core;

import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.mq.core.api.MqPublisher;
import com.ouyunc.mq.core.properties.MqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * MQ 工厂：按 {@code ouyunc.mq.type} 选择 Kafka / RocketMQ 发送实现。
 * <p>
 * 运行时需将对应实现模块（{@code ouyunc-mq-kafka} 或 {@code ouyunc-mq-rocket}）加入 classpath。
 * </p>
 */
public enum MqFactory {

    PUBLISHER {

        private static volatile MqPublisher publisher;

        @Override
        public MqPublisher instance() {
            if (publisher == null) {
                synchronized (MqFactory.class) {
                    if (publisher == null) {
                        publisher = createPublisher();
                    }
                }
            }
            return publisher;
        }

        private static MqPublisher createPublisher() {
            MqProperties properties = YmlUtil.getActiveProfileValue(
                    PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION,
                    PropertiesConfigConstant.MQ_CONFIG_PROPERTIES_PREFIX,
                    MqProperties.class);
            MqType type = MqType.from(properties != null ? properties.getType() : null);
            String implementationClass = switch (type) {
                case ROCKET -> "com.ouyunc.mq.rocket.RocketMqPublisher";
                case KAFKA -> "com.ouyunc.mq.kafka.KafkaMqPublisher";
            };
            log.info("初始化 MqPublisher，类型: {}，实现: {}", type, implementationClass);
            return loadPublisher(implementationClass, type);
        }

        private static MqPublisher loadPublisher(String className, MqType type) {
            try {
                Class<?> clazz = Class.forName(className);
                return (MqPublisher) clazz.getDeclaredConstructor().newInstance();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException("未找到 " + type + " 实现类 " + className
                        + "，请确认 classpath 已引入 ouyunc-mq-" + (type == MqType.ROCKET ? "rocket" : "kafka"), e);
            } catch (Exception e) {
                throw new RuntimeException("加载 MqPublisher 失败: " + className, e);
            }
        }
    };

    private static final Logger log = LoggerFactory.getLogger(MqFactory.class);

    public abstract MqPublisher instance();
}
