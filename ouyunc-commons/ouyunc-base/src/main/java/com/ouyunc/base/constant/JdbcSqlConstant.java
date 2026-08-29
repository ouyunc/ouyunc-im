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

        SELECT_SESSION_MESSAGE_OFFSET("select `from`, device_type, `to`,  `type`, `session_message_offset` from ouyunc_im_session_message_offset where `from` = :from and `to` = :to and `type` = :type and device_type = :device_type ", "根据from to 以及 type 获取会话偏移量"),

        SELECT_FRIEND("SELECT id, user_id, friend_user_code, friend_user_id, friend_nick_name, shield, way, channel, join_time, create_time, update_time FROM ouyunc_im_friend where user_id = :user_id and friend_user_id = :friend_user_id ", "查询好友关系"),

        SELECT_GROUP("SELECT id,group_code,group_name,group_avatar,group_description,group_announcement,group_join_policy,`status`,silence,app_key,create_time,update_time,del_flag FROM ouyunc_im_group WHERE id = :id and del_flag = 0 ", "查询群组"),

        SELECT_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user WHERE user_id = :user_id AND group_id = :group_id ", "查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user WHERE group_id = :group_id AND user_id IN (:userIds)", "批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time  FROM ouyunc_im_group_user where group_id = :group_id ", "查询所有群成员"),

        SELECT_USER("SELECT id,open_id, code, username,`password`,nick_name,avatar,motto,age,sex,email,phone_num,id_card_no,group_invite_policy,friend_join_policy,`status`,app_key,type,external_id,union_id,create_time,update_time,del_flag FROM ouyunc_im_user WHERE id = :id and del_flag = 0", "查询用户"),

        SELECT_BLACKLIST("select id, identity, user_id, identity_type, join_time, create_time from  ouyunc_im_blacklist where identity = :identity and user_id = :user_id and identity_type = :identity_type ", "查询黑名单"),

        SELECT_APP("SELECT id, app_key, app_secret, app_name, user_id, max_connections, `status`, create_time, update_time, del_flag FROM ouyunc_im_app WHERE app_key = :app_key AND del_flag = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT id, app_key, app_secret, app_name, user_id, max_connections, `status`, create_time, update_time, del_flag FROM ouyunc_im_app WHERE del_flag = 0", "查询全部未删除的 IM 应用")
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
     * PostgreSQL 实现（双引号包裹与 MySQL 保留字/关键字冲突的标识符）
     */
    public enum POSTGRESQL {

        SELECT_MESSAGE("SELECT id, protocol, protocol_version, device_type, network_type, encrypt_type, serialize_algorithm, message_type, retain, client_ip, \"from\", \"to\", content_type, content, extra, \"at\", qos, client_send_time, server_arrival_time FROM ouyunc_im_message where id in (:ids)", "根据主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("select \"from\", device_type, \"to\",  \"type\", session_message_offset from ouyunc_im_session_message_offset where \"from\" = :from and \"to\" = :to and \"type\" = :type and device_type = :device_type ", "根据from to 以及 type 获取会话偏移量"),

        SELECT_FRIEND("SELECT id, user_id, friend_user_code, friend_user_id, friend_nick_name, shield, way, channel, join_time, create_time, update_time FROM ouyunc_im_friend where user_id = :user_id and friend_user_id = :friend_user_id ", "查询好友关系"),

        SELECT_GROUP("SELECT id,group_code,group_name,group_avatar,group_description,group_announcement,group_join_policy,\"status\",silence,app_key,create_time,update_time,del_flag FROM ouyunc_im_group WHERE id = :id and del_flag = 0 ", "查询群组"),

        SELECT_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user WHERE user_id = :user_id AND group_id = :group_id ", "查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user WHERE group_id = :group_id AND user_id IN (:userIds)", "批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time  FROM ouyunc_im_group_user where group_id = :group_id ", "查询所有群成员"),

        SELECT_USER("SELECT id,open_id, code, username,\"password\",nick_name,avatar,motto,age,sex,email,phone_num,id_card_no,group_invite_policy,friend_join_policy,\"status\",app_key,type,external_id,union_id,create_time,update_time,del_flag FROM ouyunc_im_user WHERE id = :id and del_flag = 0", "查询用户"),

        SELECT_BLACKLIST("select id, \"identity\", user_id, identity_type, join_time, create_time from  ouyunc_im_blacklist where \"identity\" = :identity and user_id = :user_id and identity_type = :identity_type ", "查询黑名单"),

        SELECT_APP("SELECT id, app_key, app_secret, app_name, user_id, max_connections, \"status\", create_time, update_time, del_flag FROM ouyunc_im_app WHERE app_key = :app_key AND del_flag = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT id, app_key, app_secret, app_name, user_id, max_connections, \"status\", create_time, update_time, del_flag FROM ouyunc_im_app WHERE del_flag = 0", "查询全部未删除的 IM 应用")
        ;

        private String sql;

        private String description;

        POSTGRESQL() {
        }

        POSTGRESQL(String sql, String description) {
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
     * Oracle 实现（表/列未加引号时使用数据字典大写命名；与关键字冲突的列用双引号大写，如 {@code "FROM"}）
     */
    public enum ORACLE {

        SELECT_MESSAGE("SELECT ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, MESSAGE_TYPE, RETAIN, CLIENT_IP, \"FROM\", \"TO\", CONTENT_TYPE, CONTENT, EXTRA, \"AT\", QOS, CLIENT_SEND_TIME, SERVER_ARRIVAL_TIME FROM OUYUNC_IM_MESSAGE WHERE ID IN (:ids)", "根据主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("SELECT \"FROM\", DEVICE_TYPE, \"TO\", \"TYPE\", SESSION_MESSAGE_OFFSET FROM OUYUNC_IM_SESSION_MESSAGE_OFFSET WHERE \"FROM\" = :from AND \"TO\" = :to AND \"TYPE\" = :type AND DEVICE_TYPE = :device_type", "根据from to 以及 type 获取会话偏移量"),

        SELECT_FRIEND("SELECT ID, USER_ID, FRIEND_USER_CODE, FRIEND_USER_ID, FRIEND_NICK_NAME, SHIELD, WAY, CHANNEL, JOIN_TIME, CREATE_TIME, UPDATE_TIME FROM OUYUNC_IM_FRIEND WHERE USER_ID = :user_id AND FRIEND_USER_ID = :friend_user_id", "查询好友关系"),

        SELECT_GROUP("SELECT ID, GROUP_CODE, GROUP_NAME, GROUP_AVATAR, GROUP_DESCRIPTION, GROUP_ANNOUNCEMENT, GROUP_JOIN_POLICY, STATUS, SILENCE, APP_KEY, CREATE_TIME, UPDATE_TIME, DELETED FROM OUYUNC_IM_GROUP WHERE ID = :id AND DELETED = 0", "查询群组"),

        SELECT_GROUP_USER("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER WHERE USER_ID = :user_id AND GROUP_ID = :group_id", "查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER WHERE GROUP_ID = :group_id AND USER_ID IN (:userIds)", "批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER WHERE GROUP_ID = :group_id", "查询所有群成员"),

        SELECT_USER("SELECT ID, OPEN_ID, CODE, USERNAME, \"PASSWORD\", NICK_NAME, AVATAR, MOTTO, AGE, SEX, EMAIL, PHONE_NUM, ID_CARD_NO, GROUP_INVITE_POLICY, FRIEND_JOIN_POLICY, STATUS, APP_KEY, \"TYPE\", CREATE_TIME, UPDATE_TIME, DELETED FROM OUYUNC_IM_USER WHERE ID = :id AND DELETED = 0", "查询用户"),

        SELECT_BLACKLIST("SELECT ID, \"IDENTITY\", USER_ID, IDENTITY_TYPE, JOIN_TIME, CREATE_TIME FROM OUYUNC_IM_BLACKLIST WHERE \"IDENTITY\" = :identity AND USER_ID = :user_id AND IDENTITY_TYPE = :identity_type", "查询黑名单"),

        SELECT_APP("SELECT ID, APP_KEY, APP_SECRET, APP_NAME, USER_ID, MAX_CONNECTIONS, STATUS, CREATE_TIME, UPDATE_TIME, DELETED AS DEL_FLAG FROM OUYUNC_IM_APP WHERE APP_KEY = :app_key AND DELETED = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT ID, APP_KEY, APP_SECRET, APP_NAME, USER_ID, MAX_CONNECTIONS, STATUS, CREATE_TIME, UPDATE_TIME, DELETED AS DEL_FLAG FROM OUYUNC_IM_APP WHERE DELETED = 0", "查询全部未删除的 IM 应用")
        ;

        private String sql;

        private String description;

        ORACLE() {
        }

        ORACLE(String sql, String description) {
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

}
