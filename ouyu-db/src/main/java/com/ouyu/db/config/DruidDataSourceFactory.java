package com.ouyu.db.config;

import com.alibaba.druid.pool.DruidDataSource;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSourceFactory;

/**
 * @Author fangzhenxun
 * @Description 配置 Druid 的数据连接池
 */
public class DruidDataSourceFactory  extends UnpooledDataSourceFactory {
	public DruidDataSourceFactory() {
		this.dataSource = new DruidDataSource();
	}
}
