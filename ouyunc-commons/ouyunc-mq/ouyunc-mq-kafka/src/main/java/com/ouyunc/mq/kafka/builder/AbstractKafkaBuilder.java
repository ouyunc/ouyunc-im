package com.ouyunc.mq.kafka.builder;

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
     * kafka 属性配置文件信息
     */
    protected static KafkaProperties kafkaProperties;


    static {
        // 加载配置文件
        loadProperties();
    }


    private static void loadProperties() {
        // 读取配置信息,请注意类的初始化和加载顺序
        kafkaProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.KAFKA_CONFIG_PROPERTIES_PREFIX ,KafkaProperties.class);
        if (kafkaProperties == null) {
            throw new RuntimeException("加载kafka属性配置文件失败");
        }
        // 只打印转换后的 key，避免 jaas 密码进日志
        Set<String> extraKeys = kafkaProperties.resolvedExtraConfigKeys();
        if (!extraKeys.isEmpty()) {
            log.info("已加载 Kafka extra properties（驼峰已转点分）: {}", extraKeys);
        }
    }
}
