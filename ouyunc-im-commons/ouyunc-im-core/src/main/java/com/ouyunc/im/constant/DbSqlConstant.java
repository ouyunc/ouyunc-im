package com.ouyunc.im.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class DbSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{


        SELECT_MESSAGE_READ_RECEIPT("SELECT read_list as readList, \n" +
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
                "\tsend_time as sendTime, \n" +
                "\tcreate_time as createTime, \n" +
                "\tupdate_time as updateTime \n" +
                "\t FROM OUYUNC_IM_SEND_MESSAGE WHERE DELETED = 0 AND ID = ? LIMIT 1","查询已读列表"),

        UPDATE_MESSAGE_READ_RECEIPT("UPDATE OUYUNC_IM_SEND_MESSAGE SET READ_LIST = ? WHERE DELETED = 0 AND ID = ? ","读已回执，更新已读列表"),



        SELECT_BLACK_LIST("\tSELECT\n" +
                "\touyunc_im_blacklist.identity, \n" +
                "\touyunc_im_blacklist.user_id as userId, \n" +
                "\touyunc_im_blacklist.identity_type as identityType, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_user.nick_name as nickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum,\n" +
                "\touyunc_im_blacklist.create_time as createTime\n" +
                "\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_blacklist\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_blacklist.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_blacklist.identity = ? AND\n" +
                "\touyunc_im_blacklist.identity_type = ? AND\n" +
                "\touyunc_im_blacklist.user_id = ? ", "获取黑名单的用户信息"),

        SELECT_FRIEND_USER("SELECT\n" +
                "ouyunc_im_friend.user_id as  userId,\n" +
                "ouyunc_im_friend.friend_user_id as friendUserId,\n" +
                "\touyunc_im_user.username as friendUsername, \n" +
                "\touyunc_im_friend.friend_nick_name as friendNickName, \n" +
                "\touyunc_im_user.email as friendEmail, \n" +
                "\touyunc_im_user.phone_num as friendPhoneNum,  \n" +
                "\touyunc_im_user.id_card_num as friendIdCardNum, \n" +
                "\touyunc_im_user.avatar as friendAvatar, \n" +
                "\touyunc_im_user.motto as friendMotto, \n" +
                "\touyunc_im_user.age as friendAge, \n" +
                "\touyunc_im_user.sex as friendSex, \n" +
                "\touyunc_im_friend.is_shield as friendIsShield, \n" +
                "\touyunc_im_friend.create_time as createTime, \n" +
                "\touyunc_im_friend.update_time as updateTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_friend\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_friend.friend_user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_friend.user_id = ? and  ouyunc_im_friend.friend_user_id = ?","获取用户好友信息"),

        SELECT_GROUP_USER("SELECT\n" +
                "\touyunc_im_group_user.group_id as groupId, \n" +
                "\touyunc_im_group_user.user_id as userId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_group_user.user_nick_name as userNickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum, \n" +
                "\touyunc_im_group_user.is_leader as isLeader, \n" +
                "\touyunc_im_group_user.is_manager as isManager, \n" +
                "\touyunc_im_group_user.is_shield as isShield, \n" +
                "\touyunc_im_group_user.mushin, \n" +
                "\touyunc_im_group_user.create_time as createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_group_user.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_group_user.group_id = ? and ouyunc_im_group_user.user_id = ? ","查询单个群成员"),

        SELECT_GROUP_LEADER_USER("SELECT\n" +
                "\touyunc_im_group_user.group_id as groupId, \n" +
                "\touyunc_im_group_user.user_id as userId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_group_user.user_nick_name as userNickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum, \n" +
                "\touyunc_im_group_user.is_leader as isLeader, \n" +
                "\touyunc_im_group_user.is_manager as isManager, \n" +
                "\touyunc_im_group_user.is_shield as isShield, \n" +
                "\touyunc_im_group_user.mushin, \n" +
                "\touyunc_im_group_user.create_time as createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_group_user.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_group_user.group_id = ? and ouyunc_im_group_user.is_leader  = 1 ","查询单个群成员"),


        SELECT_GROUP_USERS("SELECT\n" +
                "\touyunc_im_group_user.group_id as groupId, \n" +
                "\touyunc_im_group_user.user_id as userId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_group_user.user_nick_name as userNickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum, \n" +
                "\touyunc_im_group_user.is_leader as isLeader, \n" +
                "\touyunc_im_group_user.is_manager as isManager, \n" +
                "\touyunc_im_group_user.is_shield as isShield, \n" +
                "\touyunc_im_group_user.mushin, \n" +
                "\touyunc_im_group_user.create_time as createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_group_user.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_group_user.group_id = ?  ","查询群所有成员"),


        SELECT_GROUP_LEADER_USERS("SELECT\n" +
                "\touyunc_im_group_user.group_id as groupId, \n" +
                "\touyunc_im_group_user.user_id as userId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_group_user.user_nick_name as userNickName, \n" +
                "\touyunc_im_user.avatar, \n" +
                "\touyunc_im_user.motto, \n" +
                "\touyunc_im_user.age, \n" +
                "\touyunc_im_user.sex, \n" +
                "\touyunc_im_user.email, \n" +
                "\touyunc_im_user.phone_num as phoneNum, \n" +
                "\touyunc_im_user.id_card_num as idCardNum, \n" +
                "\touyunc_im_group_user.is_leader as isLeader, \n" +
                "\touyunc_im_group_user.is_manager as isManager, \n" +
                "\touyunc_im_group_user.is_shield as isShield, \n" +
                "\touyunc_im_group_user.mushin, \n" +
                "\touyunc_im_group_user.create_time as createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.id = ouyunc_im_group_user.user_id\n" +
                "WHERE\n" +
                "\touyunc_im_user.deleted = 0 AND\n" +
                "\touyunc_im_group_user.group_id = ?  and (ouyunc_im_group_user.is_leader  = 1 or ouyunc_im_group_user.is_manager = 1) ","查询群管理员或群主列表"),

        SELECT_USER(" SELECT\n" +
                "\touyunc_im_user.id, \n" +
                "\touyunc_im_user.open_id as openId, \n" +
                "\touyunc_im_user.username, \n" +
                "\touyunc_im_user.password, \n" +
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
                "WHERE\n" +
                "\tdeleted = 0 AND\n" +
                "\tid = ? ","查询用户信息"),

        SELECT_GROUP("SELECT \n" +
                "\touyunc_im_group.id, \n" +
                "\touyunc_im_group.group_name as groupName, \n" +
                "\touyunc_im_group.group_avatar as groupAvatar, \n" +
                "\touyunc_im_group.group_description as groupDescription, \n" +
                "\touyunc_im_group.group_announcement as groupAnnouncement, \n" +
                "\touyunc_im_group.status, \n" +
                "\touyunc_im_group.mushin, \n" +
                "\touyunc_im_group.create_time as createTime, \n" +
                "\touyunc_im_group.update_time as updateTime, \n" +
                "FROM\n" +
                "\touyunc_im_group\n" +
                "WHERE\n" +
                "\tdeleted = 0 AND\n" +
                "\tid = ? ","查询群组信息"),

        DELETE_GROUP("delete  from  ouyunc_im_group  where id = ? ","删除群"),
        DELETE_GROUP_USER("delete  from  ouyunc_im_group_user  where group_id = ? and user_id= ?","删除群某个成员关系"),
        DELETE_GROUP_ALL_USER("delete  from  ouyunc_im_group_user  where group_id = ?","删除群所有成员关系"),


        INSERT_FRIEND("INSERT INTO ouyunc_im_friend (id, user_id, friend_user_id, friend_nick_name, is_shield, create_time, update_time) VALUES (?, ?, ?, ?, ?, ?, ?)","添加好友关系"),

        INSERT_GROUP_USER("INSERT INTO ouyunc_im_group_user (id, group_id, user_id, user_nick_name, is_leader, is_manager, is_shield, mushin, create_time) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)","添加群成员关系"),



        INSERT_MESSAGE("INSERT INTO OUYUNC_IM_MESSAGE (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT, SEND_TIME, CREATE_TIME, UPDATE_TIME, DELETED) VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入消息");

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
