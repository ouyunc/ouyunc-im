package com.ouyunc.mq.kafka.builder;

import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.mq.kafka.enums.KafkaModeEnum;
import com.ouyunc.mq.kafka.properties.ClusterKafkaProperties;
import com.ouyunc.mq.kafka.properties.KafkaProperties;
import com.ouyunc.mq.kafka.properties.StandaloneKafkaProperties;
import com.ouyunc.mq.kafka.strategy.ClusterKafkaStrategy;
import com.ouyunc.mq.kafka.strategy.KafkaStrategy;
import com.ouyunc.mq.kafka.strategy.StandaloneKafkaStrategy;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

/**
 * @description: 抽象kafka 构建者
 * @author fzx
 * @date 2025/1/13 17:12
 * @version 1.0
 */
public abstract class AbstractKafkaBuilder<T> implements KafkaMqBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractKafkaBuilder.class);

    /**
     * kafka 模式类型，单利还是集群
     **/
    protected static KafkaModeEnum mode;

    /**
     * kafka 属性配置文件信息
     */
    protected static KafkaProperties kafkaProperties;

    /**
     * 获取所有kafka的模式策略
     **/
    protected static List<KafkaStrategy<?,?>> kafkaMqStrategyList;

    static {
        // 加载配置文件
        loadProperties();
        // 初始化model和策略
        initStrategy();
    }


    private static void loadProperties() {
        // 读取配置信息,请注意类的初始化和加载顺序
        kafkaProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.KAFKA_CONFIG_PROPERTIES_PREFIX ,KafkaProperties.class);
        if (kafkaProperties == null) {
            throw new RuntimeException("加载kafka属性配置文件失败");
        }
        // 从配置中心读取配置信息,请注意类的初始化和加载顺序
        mode = kafkaProperties.getMode();
        // 单实例
        if (KafkaModeEnum.STANDALONE.equals(kafkaProperties.getMode())) {
            kafkaProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.KAFKA_STANDALONE_CONFIG_PROPERTIES_PREFIX , StandaloneKafkaProperties.class);
        }else if (KafkaModeEnum.CLUSTER.equals(kafkaProperties.getMode())) {
            // 集群
            kafkaProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.KAFKA_CLUSTER_CONFIG_PROPERTIES_PREFIX , ClusterKafkaProperties.class);
        }else {
            log.error("非法kafka 配置模式 {},请检查配置文件!", kafkaProperties.getMode().getMode());
            throw new RuntimeException("非法kafka 配置模式,请检查配置文件!");
        }
        kafkaProperties.setMode(mode);
    }

    /**
     * 初始化mode和策略
     */
    private static void initStrategy() {
        kafkaMqStrategyList = new ArrayList<>() {{
            add(new StandaloneKafkaStrategy());
            add(new ClusterKafkaStrategy());
        }};
    }

    /**
     * @author fzx
     * @description  获得当前redis选中的配置策略
     **/
    protected KafkaStrategy<?,?> currentKafkaStrategy() {
        if (CollectionUtils.isNotEmpty(kafkaMqStrategyList)) {
            return kafkaMqStrategyList.parallelStream().filter(kafkaStrategy -> {
                KafkaModeEnum primaryMode = kafkaStrategy.getMode();
                if (primaryMode.equals(mode)) {
                    log.info("当前kafka配置加载模式为========》" + kafkaStrategy.getMode().getMode());
                }
                return primaryMode.equals(mode);
            }).findAny().orElseThrow(() ->new RuntimeException("没有找到对应的配置方式!"));
        }
        log.error("没有找到对应的配置方式,请检查配置文件!");
        throw new RuntimeException("没有找到对应的配置方式!");
    }
}
