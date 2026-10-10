package com.ouyunc.mq.kafka.builder;

import com.ouyunc.base.config.ConfigBinder;
import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.mq.kafka.properties.KafkaProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Set;

/**
 * @description: 抽象kafka 构建者
 * @author fzx
 * @date 2025/1/13 17:12
 * @version 1.0
 */
public abstract class AbstractKafkaBuilder<T> implements KafkaMqBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractKafkaBuilder.class);


    /**
     * kafka 属性。由入口 {@link #bind()} 写入，子类通过 {@link #properties()} 读取。
     */
    private static volatile KafkaProperties kafkaProperties;

    private static volatile boolean bound;

    /**
     * 由配置入口绑定 {@code ouyunc.mq.kafka}。前缀缺失时记下空值，建连时再失败。
     */
    public static void bind() {
        synchronized (AbstractKafkaBuilder.class) {
            kafkaProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.KAFKA_CONFIG_PROPERTIES_PREFIX, KafkaProperties.class);
            bound = true;
        }
        if (kafkaProperties == null) {
            return;
        }
        Set<String> extraKeys = kafkaProperties.resolvedExtraConfigKeys();
        if (!extraKeys.isEmpty()) {
            log.info("已加载 Kafka extra properties（驼峰已转点分）: {}", extraKeys);
        }
    }

    protected static KafkaProperties properties() {
        ConfigBinder.requireBound(bound);
        if (kafkaProperties == null) {
            throw new RuntimeException("加载kafka属性配置文件失败");
        }
        return kafkaProperties;
    }
}
