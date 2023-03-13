package com.ouyunc.im.db.config;

import com.ouyunc.im.db.properties.DbProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * jdbc 连接池配置, 单例模式
 */
public enum JdbcPool {
    INSTANCE;
    private static Logger log = LoggerFactory.getLogger(JdbcPool.class);

    /**
     * jdbcTemplate
     */
    private static volatile JdbcTemplate jdbcTemplate = null;

    /**
     * 初始化配置
     */
    private void init() {
        log.info("开始初始化jdbc配置");
        DbProperties dbProperties = ConfigFactory.create(DbProperties.class, System.getProperties());
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setDriverClassName(dbProperties.driverClassName());
        hikariConfig.setJdbcUrl(dbProperties.jdbcUrl());
        hikariConfig.setUsername(dbProperties.username());
        hikariConfig.setPassword(dbProperties.password());
        hikariConfig.setReadOnly(dbProperties.readOnly());
        hikariConfig.setConnectionTimeout(dbProperties.connectionTimeout());
        hikariConfig.setIdleTimeout(dbProperties.idleTimeout());
        hikariConfig.setMaxLifetime(dbProperties.maxLifeTime());
        hikariConfig.setMinimumIdle(dbProperties.minimumIdle());
        hikariConfig.setMaximumPoolSize(dbProperties.maximumPoolSize());
        hikariConfig.setPoolName(dbProperties.poolName());
        DataSource dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate =  new JdbcTemplate(dataSource);
    }

    public JdbcTemplate jdbcTemplate() {
        if(jdbcTemplate == null) {
            synchronized(JdbcPool.class) {
                if(jdbcTemplate == null) {
                    init();
                }
            }
        }
        return jdbcTemplate;
    }



}
