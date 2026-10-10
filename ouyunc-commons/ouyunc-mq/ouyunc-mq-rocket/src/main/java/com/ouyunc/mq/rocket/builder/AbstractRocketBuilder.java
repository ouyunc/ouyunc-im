package com.ouyunc.mq.rocket.builder;

import com.ouyunc.base.config.ConfigBinder;
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
     * RocketMQ 属性。由入口 {@link #bind()} 写入，子类通过 {@link #properties()} 读取。
     */
    private static volatile RocketProperties rocketProperties;

    private static volatile boolean bound;

    /**
     * 由配置入口绑定 {@code ouyunc.mq.rocket}。前缀缺失时记下空值，建连时再失败。
     */
    public static void bind() {
        synchronized (AbstractRocketBuilder.class) {
            rocketProperties = YmlUtil.getActiveProfileValue(
                    PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION,
                    PropertiesConfigConstant.ROCKET_CONFIG_PROPERTIES_PREFIX,
                    RocketProperties.class);
            bound = true;
        }
        if (rocketProperties != null) {
            log.debug("RocketMQ 配置已加载");
        }
    }

    protected static RocketProperties properties() {
        ConfigBinder.requireBound(bound);
        if (rocketProperties == null) {
            throw new RuntimeException("加载 RocketMQ 属性配置文件失败");
        }
        return rocketProperties;
    }
}
