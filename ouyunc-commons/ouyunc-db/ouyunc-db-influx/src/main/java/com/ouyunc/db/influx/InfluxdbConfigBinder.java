package com.ouyunc.db.influx;

import com.ouyunc.base.config.ConfigBinder;

/**
 * 把 InfluxDB 配置挂到启动入口，工厂只读取绑定结果。
 */
public final class InfluxdbConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        InfluxdbFactory.bind();
    }
}
