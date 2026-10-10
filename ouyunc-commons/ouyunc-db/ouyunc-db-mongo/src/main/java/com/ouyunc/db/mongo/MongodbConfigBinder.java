package com.ouyunc.db.mongo;

import com.ouyunc.base.config.ConfigBinder;

/**
 * 把 MongoDB 配置挂到启动入口，工厂只读取绑定结果。
 */
public final class MongodbConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        MongodbFactory.bind();
    }
}
