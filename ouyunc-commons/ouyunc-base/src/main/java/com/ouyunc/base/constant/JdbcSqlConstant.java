package com.ouyunc.base.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class JdbcSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{

        SELECT_MESSAGE("SELECT `id`, `protocol`, `protocol_version`, `device_type`, `network_type`, `encrypt_type`, `serialize_algorithm`, `message_type`, `retain`, `client_ip`, `from`, `to`, `content_type`, `content`, `extra`, `at`, `qos`, `client_send_time`, `server_arrival_time` FROM `ouyunc_im_message` where id in (:ids)", "根据主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("select `from`, device_type, `to`,  `type`, `session_message_offset` from ouyunc_im_session_message_offset where `from` = ? and `to` = ? and `type` = ? and device_type= ? ", "根据from to 以及 type 获取会话偏移量"),

        SELECT_FRIEND("SELECT id, user_id, friend_user_code, friend_user_id, friend_nick_name, shield, way, channel, join_time, create_time, update_time FROM ouyunc_im_friend where user_id = ? and friend_user_id = ? ", "查询好友关系"),

        SELECT_GROUP("SELECT id,group_code,group_name,group_avatar,group_description,group_announcement,group_join_policy,`status`,silence,app_key,create_time,update_time,deleted FROM ouyunc_im_group WHERE id = ? and deleted = 0 ", "查询群组"),

        SELECT_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel create_time, join_time FROM ouyunc_im_group_user where  user_id = ? and group_id = ? ", "查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel create_time, join_time  FROM ouyunc_im_group_user where group_id = ? ", "查询所有群成员"),

        SELECT_USER("SELECT id,open_id, code, username,`password`,nick_name,avatar,motto,age,sex,email,phone_num,id_card_no,group_invite_policy,friend_join_policy,`status`,app_key,robot,create_time,update_time,deleted FROM ouyunc_im_user WHERE id = ? and deleted = 0", "查询用户"),

        SELECT_BLACKLIST("select id, identity, user_id, identity_type, join_time, create_time from  ouyunc_im_blacklist where identity = ? and user_id = ? and identity_type = ? ", "查询黑名单")
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
