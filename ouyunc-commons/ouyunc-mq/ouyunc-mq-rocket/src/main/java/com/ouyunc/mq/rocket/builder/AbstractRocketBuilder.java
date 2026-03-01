package com.ouyunc.mq.rocket.builder;

import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.mq.rocket.properties.RocketProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @description: 抽象 RocketMQ 构建者
 * @author fzx
 * @version 1.0
 */
public abstract class AbstractRocketBuilder<T> implements RocketMqBuilder<T> {

    private static final Logger log = LoggerFactory.getLogger(AbstractRocketBuilder.class);

    /**
     * RocketMQ 属性配置
     */
    protected static RocketProperties rocketProperties;

    static {
        loadProperties();
    }

    private static void loadProperties() {
        rocketProperties = YmlUtil.getActiveProfileValue(
                PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION,
                PropertiesConfigConstant.ROCKET_CONFIG_PROPERTIES_PREFIX,
                RocketProperties.class);
        if (rocketProperties == null) {
            throw new RuntimeException("加载 RocketMQ 属性配置文件失败");
        }
    }
}
