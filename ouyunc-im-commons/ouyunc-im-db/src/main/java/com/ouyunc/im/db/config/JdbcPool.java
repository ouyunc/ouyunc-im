package com.ouyunc.im.db.config;

import com.ouyunc.im.db.properties.DbProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.aeonbits.owner.ConfigFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * jdbc 连接池配置, 单例模式
 */
public class JdbcPool {
    private static Logger log = LoggerFactory.getLogger(JdbcPool.class);

    private static volatile JdbcPool instance = null;

    /**
     * 数据源
     */
    private static DataSource dataSource = null;

    /**
     * jdbcTemplate
     */
    private static JdbcTemplate jdbcTemplate = null;

    /**
     * 数据库配置信息
     */
    private static DbProperties dbProperties = null;

    /**
     * 初始化
     */
    private JdbcPool() {
        init();
    }

    static {
        dbProperties = ConfigFactory.create(DbProperties.class, System.getProperties());
    }

    /**
     * 初始化配置
     */
    private void init() {
        log.info("开始初始化jdbc配置");
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
        dataSource = new HikariDataSource(hikariConfig);
        jdbcTemplate = new JdbcTemplate(dataSource);
    }

    public static JdbcTemplate jdbcTemplate() {
        if(instance == null) {
            synchronized(JdbcPool.class) {
                if(instance == null) {
                    instance = new JdbcPool();
                }
            }
        }
        return jdbcTemplate;
    }



}
