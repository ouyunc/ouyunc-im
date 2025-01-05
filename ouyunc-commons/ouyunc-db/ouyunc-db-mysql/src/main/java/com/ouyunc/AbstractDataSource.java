package com.ouyunc;

import javax.sql.DataSource;

/**
 * 数据源抽象类
 */
public abstract class AbstractDataSource {
    /**
     * 获取数据源
     */
    public abstract DataSource getDataSource();

    /**
     * 创建数据源
     */
    public abstract DataSource createDataSource();

    /**
     * 关闭数据源
     */
    public abstract void closeDataSource();
}
