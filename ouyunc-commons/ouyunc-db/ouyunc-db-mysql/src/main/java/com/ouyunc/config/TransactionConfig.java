package com.ouyunc.config;


import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;

public class TransactionConfig {

    private DataSource dataSource;


    /**
     * 数据库操作模板 jdbcTemplate
     */
    public JdbcTemplate jdbcTemplate() {
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        // 设置查询超时时间（秒）
        jdbcTemplate.setQueryTimeout(30);
        // 设置最大行数限制
        jdbcTemplate.setMaxRows(500);
        // 设置获取警告信息
        jdbcTemplate.setIgnoreWarnings(false);
        return jdbcTemplate;
    }


    /**
     * 事务管理器
     */
    public DataSourceTransactionManager transactionManager() {
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(dataSource);
        // 设置全局事务超时时间（秒）
        txManager.setDefaultTimeout(30);
        // 设置验证已存在的事务
        txManager.setValidateExistingTransaction(true);
        // 设置回滚时是否只回滚到保存点
        txManager.setNestedTransactionAllowed(true);
        return txManager;
    }

    /**
     * 读写事务模板
     */
    public TransactionTemplate transactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager());
        // 设置事务传播行为
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        // 设置事务隔离级别
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        // 设置事务超时时间（秒）
        template.setTimeout(30);
        // 设置是否只读事务
        template.setReadOnly(false);
        return template;
    }

    /**
     * 只读操作的事务模板
     */
    public TransactionTemplate readOnlyTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager());
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        template.setIsolationLevel(TransactionDefinition.ISOLATION_READ_COMMITTED);
        template.setTimeout(10);
        template.setReadOnly(true);
        return template;
    }
}