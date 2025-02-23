package com.ouyunc.base.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class JdbcSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{

        SELECT_MESSAGE("SELECT `id`, `protocol`, `protocol_version`, `device_type`, `network_type`, `encrypt_type`, `serialize_algorithm`, `message_type`, `retain`, `client_ip`, `from`, `to`, `content_type`, `content`, `extra`, `at`, `qos`, `client_send_time`, `server_arrival_time`, `read`, `withdrawn` FROM `ouyunc_im_message` where id in (:ids)", "根据主键id查询消息"),
        INSERT_MESSAGE("INSERT INTO `ouyunc_im_message`(`id`, `protocol`, `protocol_version`, `device_type`, `network_type`, `encrypt_type`, `serialize_algorithm`, `message_type`, `retain`, `client_ip`, `from`, `to`, `content_type`, `content`, `extra`, `at`, `qos`, `client_send_time`, `server_arrival_time`, `read`, `withdrawn`) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", "保存消息"),
        UPDATE_WITHDRAW_MESSAGE("UPDATE `ouyunc_im_message` SET `withdrawn` = ? WHERE id = ? ", "撤回消息"),
        INSERT_READ_RECEIPT_MESSAGE("INSERT INTO `ouyunc_im_read_receipt` (id, msg_id, user_id, read_time) VALUES(?, ?, ?, ?)", "保存读已回执记录"),
        UPDATE_READ_MESSAGE("UPDATE `ouyunc_im_message` SET `read` = ? WHERE id = ? ", "读已回执消息")

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
