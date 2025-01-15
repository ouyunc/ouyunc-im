package com.ouyunc.db.mysql;

import com.ouyunc.base.constant.PropertiesConfigConstant;
import com.ouyunc.base.utils.YmlUtil;
import com.ouyunc.db.mysql.operator.DbOperator;
import com.ouyunc.db.mysql.properties.JdbcProperties;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.util.List;

/**
 * jdbc 工厂，旨在提供消息的持久化工具类；
 * 如果使用其他持久化方式存储 如：基于阿里表格存储（Tablestore）自研的Timeline模型构建的消息进行存储，或其他方式，可自定扩展或自定义
 */
public enum JdbcFactory implements DbOperator {

    JDBC_TEMPLATE (1, "jdbcTemplate v1.0操作模板"){
        private static volatile JdbcTemplate jdbcTemplate;
        /**
         * 获取jdbcTemplate
         */
        @SuppressWarnings("unchecked")
        @Override
        public JdbcTemplate instance() {
            if (jdbcTemplate == null) {
                synchronized (JdbcFactory.class) {
                    if (jdbcTemplate == null) {
                        jdbcTemplate = new JdbcTemplate(getDataSource());
                        // 设置查询超时时间（秒）
                        jdbcTemplate.setQueryTimeout(30);
                        // 设置获取警告信息
                        jdbcTemplate.setIgnoreWarnings(false);
                    }
                }
            }
            return jdbcTemplate;
        }



        @Override
        public void execute(String sql) {
            instance().execute(sql);
        }

        @Override
        public <T> T selectOne(String sql, Class<T> tClass, Object... args) {
            try{
                return instance().queryForObject(sql, new BeanPropertyRowMapper<>(tClass), args);
            }catch (EmptyResultDataAccessException e ){
                log.error("未查询到数据，返回null");
                return null;
            }
        }

        @Override
        public <T> List<T> selectList(String sql, Class<T> tClass, Object... args) {
            try {
                return instance().query(sql, new BeanPropertyRowMapper<>(tClass), args);
            }catch (IllegalStateException e) {
                log.error("未查询到数据集合，返回null");
                return null;
            }
        }

        @Override
        public int insert(String sql, Object... args) {
            return instance().update(sql, args);
        }

        @Override
        public int[] batchInsert(String sql, List<Object[]> batchArgs) {
            return instance().batchUpdate(sql, batchArgs);
        }

        @Override
        public int update(String sql, Object... args) {
            return instance().update(sql, args);
        }

        @Override
        public int[] batchUpdate(String sql, List<Object[]> batchArgs) {
            return instance().batchUpdate(sql, batchArgs);
        }

        @Override
        public int delete(String sql, Object... args) {
            return instance().update(sql, args);
        }

        @Override
        public int[] batchDelete(String sql, List<Object[]> batchArgs) {
            return instance().batchUpdate(sql, batchArgs);
        }

    },
    JDBC_CLIENT(1, "jdbcClient v1.0操作模板") {
        private static volatile JdbcClient jdbcClient;
        /**
         * 获取jdbcTemplate
         */
        @SuppressWarnings("unchecked")
        @Override
        public JdbcClient instance() {
            if (jdbcClient == null) {
                synchronized (JdbcFactory.class) {
                    if (jdbcClient == null) {
                        JdbcTemplate jdbcTemplate =  JDBC_TEMPLATE.instance();
                        jdbcClient = JdbcClient.create(jdbcTemplate);
                    }
                }
            }
            return jdbcClient;
        }

        @Override
        public void execute(String sql) {
            jdbcClient.sql(sql).update();
        }

        @Override
        public <T> T selectOne(String sql, Class<T> tClass, Object... args) {
            return jdbcClient.sql(sql).params(args).query(tClass).optional().orElse(null);
        }

        @Override
        public <T> List<T> selectList(String sql, Class<T> tClass, Object... args) {
            return jdbcClient.sql(sql).params(args).query(tClass).list();
        }

        @Override
        public int insert(String sql, Object... args) {
            return jdbcClient.sql(sql).params(args).update();
        }

        @Override
        public int update(String sql, Object... args) {
            return jdbcClient.sql(sql).params(args).update();
        }

        @Override
        public int delete(String sql, Object... args) {
            return jdbcClient.sql(sql).params(args).update();
        }

    }
    ;

    private final int version;

    private final String description;

    JdbcFactory(int version, String description) {
        this.version = version;
        this.description = description;
    }

    public int getVersion() {
        return version;
    }



    public String getDescription() {
        return description;
    }


    private static final Logger log = LoggerFactory.getLogger(JdbcFactory.class);





    /**
     * 事务管理器
     */
    private DataSourceTransactionManager transactionManager() {
        DataSourceTransactionManager txManager = new DataSourceTransactionManager(getDataSource());
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
    public TransactionTemplate withTransaction() {
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
     * 定义数据源, 数据源连接池使用 hikari
     */
    private static volatile HikariDataSource dataSource;

    /**
     * 获取数据源
     */
    private static DataSource getDataSource() {
        if (dataSource == null) {
            dataSource = (HikariDataSource) createDataSource();
        }
        return dataSource;
    }

    /**
     * 创建数据源,可以做多数据源，这里先不做
     */
    private static DataSource createDataSource() {
        JdbcProperties jdbcProperties = YmlUtil.getActiveProfileValue(PropertiesConfigConstant.GLOBAL_CONFIG_FILE_LOCATION, PropertiesConfigConstant.JDBC_CONFIG_PROPERTIES_PREFIX, JdbcProperties.class);
        if (jdbcProperties == null) {
            throw new RuntimeException("未找到配置文件 ouyunc-server.yml 中的 ouyunc.db.jdbc 配置");
        }
        HikariConfig config = new HikariConfig();

        // ====================== 必须的基本配置 ======================
        config.setJdbcUrl(jdbcProperties.getUrl());
        config.setUsername(jdbcProperties.getUsername());
        config.setPassword(jdbcProperties.getPassword());
        config.setDriverClassName(jdbcProperties.getDriverClassName());

        // ====================== 连接池基本配置 ======================
        // 连接池的名称
        config.setPoolName(jdbcProperties.getHikariPool().getPoolName());
        // 最大连接数
        config.setMaximumPoolSize(jdbcProperties.getHikariPool().getMaximumPoolSize());
        // 最小空闲连接数
        config.setMinimumIdle(jdbcProperties.getHikariPool().getMinimumIdle());

        // ====================== 连接时间相关配置 ======================
        // 连接超时时间（毫秒）：等待可用连接的最大时间
        config.setConnectionTimeout(jdbcProperties.getHikariPool().getConnectionTimeout());
        // 空闲超时时间（毫秒）：连接允许在池中闲置的最长时间
        config.setIdleTimeout(jdbcProperties.getHikariPool().getIdleTimeout());
        // 连接最大生命周期（毫秒）：连接最长生命周期，强制关闭
        config.setMaxLifetime(jdbcProperties.getHikariPool().getMaxLifetime());
        // 心跳检测时间（毫秒）：测试连接是否有效的间隔时间
        config.setKeepaliveTime(jdbcProperties.getHikariPool().getKeepaliveTime());
        // 连接验证超时时间（毫秒）：测试连接有效性的超时时间
        config.setValidationTimeout(jdbcProperties.getHikariPool().getValidationTimeout());

        // ====================== 连接池运行相关配置 ======================
        // 是否自动提交事务
        config.setAutoCommit(jdbcProperties.getHikariPool().isAutoCommit());
        // 连接池初始化失败超时时间（毫秒）
        config.setInitializationFailTimeout(jdbcProperties.getHikariPool().getInitializationFailTimeout());
        // 是否隔离内部查询
        config.setIsolateInternalQueries(jdbcProperties.getHikariPool().isIsolateInternalQueries());
        // 是否允许池暂停
        config.setAllowPoolSuspension(jdbcProperties.getHikariPool().isAllowPoolSuspension());
        // 是否设置默认连接只读
        config.setReadOnly(jdbcProperties.getHikariPool().isReadOnly());
        // 是否注册JMX监控
        config.setRegisterMbeans(jdbcProperties.getHikariPool().isRegisterMbeans());

        // ====================== 连接检测配置 ======================
        // 连接健康检查
        String connectionTestQuery = jdbcProperties.getHikariPool().getConnectionTestQuery();
        if (StringUtils.isNotBlank(connectionTestQuery)) {
            // 连接测试查询
            config.setConnectionTestQuery(connectionTestQuery);
        }
        // 连接泄露检测阈值（毫秒）
        config.setLeakDetectionThreshold(jdbcProperties.getHikariPool().getLeakDetectionThreshold());
        return new HikariDataSource(config);
    }

    /**
     * 关闭数据源
     */
    private static void closeDataSource() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 获取实例
     */
    public abstract <T> T instance();

}
