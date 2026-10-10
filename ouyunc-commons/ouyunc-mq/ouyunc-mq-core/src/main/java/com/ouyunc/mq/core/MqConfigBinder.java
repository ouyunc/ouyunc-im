package com.ouyunc.mq.core;

import com.ouyunc.base.config.ConfigBinder;

/**
 * 把 MQ 类型配置挂到启动入口。
 */
public final class MqConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        MqFactory.bind();
    }
}
