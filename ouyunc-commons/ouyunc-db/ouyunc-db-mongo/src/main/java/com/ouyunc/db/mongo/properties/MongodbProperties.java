package com.ouyunc.db.mongo.properties;



/**
 * @author fzx
 * @version 1.0
 * @description: mongodb 配置文件
 */
public class MongodbProperties {
    private String uri;
    private String defaultDatabase;
    private PoolSettings pool;





    public MongodbProperties() {
    }

    public MongodbProperties(String uri, String defaultDatabase, PoolSettings pool) {
        this.uri = uri;
        this.defaultDatabase = defaultDatabase;
        this.pool = pool;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public String getDefaultDatabase() {
        return defaultDatabase;
    }

    public void setDefaultDatabase(String defaultDatabase) {
        this.defaultDatabase = defaultDatabase;
    }

    public PoolSettings getPool() {
        return pool;
    }

    public void setPool(PoolSettings pool) {
        this.pool = pool;
    }



    public static class PoolSettings {
        private int maxSize;
        private int minSize;
        private long maxWaitTime;
        private long maxLifeTime;
        private long maxIdleTime;
        private long maintenanceInitialDelay;
        private long maintenanceFrequency;
        private int maxConnecting;
        public PoolSettings() {
        }

        public PoolSettings(int maxSize, int minSize, long maxWaitTime, long maxLifeTime, long maxIdleTime, long maintenanceInitialDelay, long maintenanceFrequency, int maxConnecting) {
            this.maxSize = maxSize;
            this.minSize = minSize;
            this.maxWaitTime = maxWaitTime;
            this.maxLifeTime = maxLifeTime;
            this.maxIdleTime = maxIdleTime;
            this.maintenanceInitialDelay = maintenanceInitialDelay;
            this.maintenanceFrequency = maintenanceFrequency;
            this.maxConnecting = maxConnecting;
        }

        public int getMaxSize() {
            return maxSize;
        }

        public void setMaxSize(int maxSize) {
            this.maxSize = maxSize;
        }

        public int getMinSize() {
            return minSize;
        }

        public void setMinSize(int minSize) {
            this.minSize = minSize;
        }

        public long getMaxWaitTime() {
            return maxWaitTime;
        }

        public void setMaxWaitTime(long maxWaitTime) {
            this.maxWaitTime = maxWaitTime;
        }

        public long getMaxLifeTime() {
            return maxLifeTime;
        }

        public void setMaxLifeTime(long maxLifeTime) {
            this.maxLifeTime = maxLifeTime;
        }

        public long getMaxIdleTime() {
            return maxIdleTime;
        }

        public void setMaxIdleTime(long maxIdleTime) {
            this.maxIdleTime = maxIdleTime;
        }

        public long getMaintenanceInitialDelay() {
            return maintenanceInitialDelay;
        }

        public void setMaintenanceInitialDelay(long maintenanceInitialDelay) {
            this.maintenanceInitialDelay = maintenanceInitialDelay;
        }

        public long getMaintenanceFrequency() {
            return maintenanceFrequency;
        }

        public void setMaintenanceFrequency(long maintenanceFrequency) {
            this.maintenanceFrequency = maintenanceFrequency;
        }

        public int getMaxConnecting() {
            return maxConnecting;
        }

        public void setMaxConnecting(int maxConnecting) {
            this.maxConnecting = maxConnecting;
        }
    }







}
