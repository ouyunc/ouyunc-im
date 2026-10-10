package com.ouyunc.mq.rocket;

import com.ouyunc.base.config.ConfigBinder;
import com.ouyunc.mq.rocket.builder.AbstractRocketBuilder;

/**
 * 把 RocketMQ 配置挂到启动入口，构建器只读取绑定结果。
 */
public final class RocketConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        AbstractRocketBuilder.bind();
    }
}
