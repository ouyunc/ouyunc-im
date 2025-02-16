package com.ouyunc.base.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class JdbcSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{

        INSERT_MESSAGE("INSERT INTO `ouyunc_im_message`(`id`, `protocol`, `protocol_version`, `device_type`, `network_type`, `encrypt_type`, `serialize_algorithm`, `message_type`, `retain`, `client_ip`, `from`, `to`, `content_type`, `content`, `extra`, `at`, `qos`, `client_send_time`, `server_arrival_time`, `read`, `withdrawn`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", "保存消息")

        ;




        /**
         * sql 脚本
         */
        private String sql;

        /**
         * sql 描述
         */
        private String description;

        MYSQL() {
        }

        MYSQL(String sql, String description) {
            this.sql = sql;
            this.description = description;
        }

        public String sql() {
            return sql;
        }

        public void setSql(String sql) {
            this.sql = sql;
        }

        public String description() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

    }

    /**
     * oracle 实现
     */
    private enum ORACLE{

    }

}
