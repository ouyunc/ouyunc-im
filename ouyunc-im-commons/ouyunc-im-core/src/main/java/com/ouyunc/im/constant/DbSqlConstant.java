package com.ouyunc.im.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class DbSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{


        SELECT_READ_RECEIPT("SELECT read_list AS readList, \n" +
                "\tid, \n" +
                "\tprotocol, \n" +
                "\tprotocol_version as protocolVersion, \n" +
                "\tdevice_type as deviceType, \n" +
                "\tnetwork_type as networkType, \n" +
                "\tencrypt_type as encryptType, \n" +
                "\tserialize_algorithm  as serializeAlgorithm, \n" +
                "\tip, \n" +
                "\tfrom, \n" +
                "\tto, \n" +
                "\ttype, \n" +
                "\tcontent_type as contentType, \n" +
                "\tcontent, \n" +
                "\tread_list as readList, \n" +
                "\tsend_time as sendTime, \n" +
                "\tcreate_time as createTime, \n" +
                "\tupdate_time as updateTime, \n" +
                "\tdeleted  FROM OUYUNC_IM_SEND_MESSAGE WHERE DELETED = 0 AND ID = ? LIMIT 1","查询已读列表"),
        SELECT_GROUP_USER("SELECT\n" +
                "\touyunc_im_user.id , \n" +
                "\touyunc_im_user.open_id as openId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_user.`password`, \n" +
                "\touyunc_im_user.nick_name as nickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum, \n" +
                "\touyunc_im_user.create_time as createTime, \n" +
                "\touyunc_im_user.update_time as updateTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_group_user.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_group_user.group_id = ? ","查询群成员列表"),

        UPDATE_READ_RECEIPT("UPDATE OUYUNC_IM_SEND_MESSAGE SET READ_LIST = ? WHERE DELETED = 0 AND ID = ? ","读已回执，更新已读列表"),


        SELECT_FRIEND_USER(" SELECT\n" +
                "\touyunc_im_user.id, \n" +
                "\touyunc_im_user.open_id, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_user.`password`, \n" +
                "\touyunc_im_user.nick_name, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num, \n" +
                "\touyunc_im_user.id_card_num, \n" +
                "\touyunc_im_user.create_time, \n" +
                "\touyunc_im_user.update_time\n" +
                "FROM\n" +
                "\touyunc_im_user LEFT JOIN ouyunc_im_friend on ouyunc_im_user.id = ouyunc_im_friend.user_id\n" +
                "WHERE\n" +
                "\tdeleted = 0 AND ouyunc_im_friend.user_id = ? AND ouyunc_im_friend.friend_user_id = ? ","获取用户好友信息"),


        SELECT_USER(" SELECT\n" +
                "\touyunc_im_user.id, \n" +
                "\touyunc_im_user.open_id, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_user.`password`, \n" +
                "\touyunc_im_user.nick_name, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num, \n" +
                "\touyunc_im_user.id_card_num, \n" +
                "\touyunc_im_user.create_time, \n" +
                "\touyunc_im_user.update_time\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "WHERE\n" +
                "\tdeleted = 0 AND\n" +
                "\tid = ? OR\n" +
                "\temail = ? OR\n" +
                "\tphone_num = ? OR\n" +
                "\tid_card_num = ? OR\n" +
                "\topen_id = ? ","查询用户"),
        INSERT_FRIEND("INSERT INTO ouyunc_im_friend (id, user_id, friend_user_id, friend_nick_name, create_time) VALUES (?, ?, ?, ?, ?)","添加好友"),
        INSERT_MESSAGE("INSERT INTO OUYUNC_IM_MESSAGE (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, FROM, TO, TYPE, CONTENT_TYPE, CONTENT, SEND_TIME, CREATE_TIME, UPDATE_TIME, DELETED) VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入消息");

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
