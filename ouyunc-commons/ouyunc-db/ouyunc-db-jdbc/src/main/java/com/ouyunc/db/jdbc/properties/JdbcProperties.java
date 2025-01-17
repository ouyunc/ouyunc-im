package com.ouyunc.db.jdbc.properties;

public class JdbcProperties {

    /**
     * 数据库驱动名称.
     */
    private String driverClassName;

    /**
     * jdbc url
     */
    private String url;


    /**
     * jdbc 用户名
     */
    private String username;

    /**
     * jdbc 密码
     */
    private String password;

    private HikariPool hikariPool;

    public JdbcProperties() {
    }

    public JdbcProperties(String driverClassName, String url, String username, String password, HikariPool hikariPool) {
        this.driverClassName = driverClassName;
        this.url = url;
        this.username = username;
        this.password = password;
        this.hikariPool = hikariPool;
    }


    public String getDriverClassName() {
        return driverClassName;
    }

    public void setDriverClassName(String driverClassName) {
        this.driverClassName = driverClassName;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public HikariPool getHikariPool() {
        return hikariPool;
    }

    public void setHikariPool(HikariPool hikariPool) {
        this.hikariPool = hikariPool;
    }

    public static class HikariPool {

        /**
         * 连接吃名称
         */
        private String poolName;

        /**
         * 连接池最大连接数
         */
        private int maximumPoolSize;


        /**
         * 最小空闲连接数
         */
        private int minimumIdle;

        /**
         * 连接超时时间,单位秒
         */
        private int connectionTimeout;

        /**
         * 空闲连接超时时间,单位毫秒
         */
        private int idleTimeout;

        /**
         * 连接最大生命周期，单位毫秒
         */
        private int maxLifetime;

        /**
         * 心跳检测时间,单位毫秒
         */
        private int keepaliveTime;

        /**
         * 连接验证超时时间,单位毫秒
         */
        private int validationTimeout;

        /**
         * 自动提交设置
         */
        private boolean autoCommit;

        /**
         * 如果池无法成功初始化连接，则此属性控制池是否将 fast fail
         */
        private int initializationFailTimeout;

        /**
         * 内部查询隔离
         */
        private boolean isolateInternalQueries;

        /**
         * 控制池是否可以通过JMX暂停和恢复
         */
        private boolean allowPoolSuspension;

        /**
         * 从池中获取的连接是否默认处于只读模式
         */
        private boolean readOnly;

        /**
         * 是否注册JMX管理Bean（MBeans）
         */
        private boolean registerMbeans;

        /**
         * 如果您的驱动程序支持JDBC4，我们强烈建议您不要设置此属性
         */
        private String connectionTestQuery;

        /**
         * 记录消息之前连接可能离开池的时间量，表示可能的连接泄漏
         */
        private int leakDetectionThreshold;

        public HikariPool() {
        }

        public HikariPool(String poolName, int maximumPoolSize, int minimumIdle, int connectionTimeout, int idleTimeout, int maxLifetime, int keepaliveTime, int validationTimeout, boolean autoCommit, int initializationFailTimeout, boolean isolateInternalQueries, boolean allowPoolSuspension, boolean readOnly, boolean registerMbeans, String connectionTestQuery, int leakDetectionThreshold) {
            this.poolName = poolName;
            this.maximumPoolSize = maximumPoolSize;
            this.minimumIdle = minimumIdle;
            this.connectionTimeout = connectionTimeout;
            this.idleTimeout = idleTimeout;
            this.maxLifetime = maxLifetime;
            this.keepaliveTime = keepaliveTime;
            this.validationTimeout = validationTimeout;
            this.autoCommit = autoCommit;
            this.initializationFailTimeout = initializationFailTimeout;
            this.isolateInternalQueries = isolateInternalQueries;
            this.allowPoolSuspension = allowPoolSuspension;
            this.readOnly = readOnly;
            this.registerMbeans = registerMbeans;
            this.connectionTestQuery = connectionTestQuery;
            this.leakDetectionThreshold = leakDetectionThreshold;
        }

        public String getPoolName() {
            return poolName;
        }

        public void setPoolName(String poolName) {
            this.poolName = poolName;
        }

        public int getMaximumPoolSize() {
            return maximumPoolSize;
        }

        public void setMaximumPoolSize(int maximumPoolSize) {
            this.maximumPoolSize = maximumPoolSize;
        }

        public int getMinimumIdle() {
            return minimumIdle;
        }

        public void setMinimumIdle(int minimumIdle) {
            this.minimumIdle = minimumIdle;
        }

        public int getConnectionTimeout() {
            return connectionTimeout;
        }

        public void setConnectionTimeout(int connectionTimeout) {
            this.connectionTimeout = connectionTimeout;
        }

        public int getIdleTimeout() {
            return idleTimeout;
        }

        public void setIdleTimeout(int idleTimeout) {
            this.idleTimeout = idleTimeout;
        }

        public int getMaxLifetime() {
            return maxLifetime;
        }

        public void setMaxLifetime(int maxLifetime) {
            this.maxLifetime = maxLifetime;
        }

        public int getKeepaliveTime() {
            return keepaliveTime;
        }

        public void setKeepaliveTime(int keepaliveTime) {
            this.keepaliveTime = keepaliveTime;
        }

        public int getValidationTimeout() {
            return validationTimeout;
        }

        public void setValidationTimeout(int validationTimeout) {
            this.validationTimeout = validationTimeout;
        }

        public boolean isAutoCommit() {
            return autoCommit;
        }

        public void setAutoCommit(boolean autoCommit) {
            this.autoCommit = autoCommit;
        }

        public int getInitializationFailTimeout() {
            return initializationFailTimeout;
        }

        public void setInitializationFailTimeout(int initializationFailTimeout) {
            this.initializationFailTimeout = initializationFailTimeout;
        }

        public boolean isIsolateInternalQueries() {
            return isolateInternalQueries;
        }

        public void setIsolateInternalQueries(boolean isolateInternalQueries) {
            this.isolateInternalQueries = isolateInternalQueries;
        }

        public boolean isAllowPoolSuspension() {
            return allowPoolSuspension;
        }

        public void setAllowPoolSuspension(boolean allowPoolSuspension) {
            this.allowPoolSuspension = allowPoolSuspension;
        }

        public boolean isReadOnly() {
            return readOnly;
        }

        public void setReadOnly(boolean readOnly) {
            this.readOnly = readOnly;
        }

        public boolean isRegisterMbeans() {
            return registerMbeans;
        }

        public void setRegisterMbeans(boolean registerMbeans) {
            this.registerMbeans = registerMbeans;
        }

        public String getConnectionTestQuery() {
            return connectionTestQuery;
        }

        public void setConnectionTestQuery(String connectionTestQuery) {
            this.connectionTestQuery = connectionTestQuery;
        }

        public int getLeakDetectionThreshold() {
            return leakDetectionThreshold;
        }

        public void setLeakDetectionThreshold(int leakDetectionThreshold) {
            this.leakDetectionThreshold = leakDetectionThreshold;
        }
    }
}
