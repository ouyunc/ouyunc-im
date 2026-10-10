package com.ouyunc.db.jdbc;

import com.ouyunc.base.config.ConfigBinder;

/**
 * 把 JDBC 配置挂到启动入口，数据源创建时只读取绑定结果。
 */
public final class JdbcConfigBinder implements ConfigBinder {

    @Override
    public void bind() {
        JdbcFactory.bind();
    }
}
