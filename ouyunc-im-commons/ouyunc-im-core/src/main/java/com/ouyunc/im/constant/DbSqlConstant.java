package com.ouyunc.im.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class DbSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{



        SELECT_BLACK_LIST("\tSELECT\n" +
                "\touyunc_im_blacklist.IDENTITY, \n" +
                "\touyunc_im_blacklist.USER_ID AS userId, \n" +
                "\touyunc_im_blacklist.IDENTITY_TYPE AS identityType, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_user.NICK_NAME AS nickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum,\n" +
                "\touyunc_im_blacklist.CREATE_TIME AS createTime\n" +
                "\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_blacklist\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_blacklist.USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_blacklist.IDENTITY = ? AND\n" +
                "\touyunc_im_blacklist.IDENTITY_TYPE = ? AND\n" +
                "\touyunc_im_blacklist.USER_ID = ? ", "获取黑名单的用户信息"),

        SELECT_FRIEND_USER("SELECT\n" +
                "ouyunc_im_friend.USER_ID AS  userId,\n" +
                "ouyunc_im_friend.FRIEND_USER_ID AS friendUserId,\n" +
                "\touyunc_im_user.USERNAME AS FRIENDUSERNAME, \n" +
                "\touyunc_im_friend.FRIEND_NICK_NAME AS friendNickName, \n" +
                "\touyunc_im_user.EMAIL AS friendEmail, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.PHONE_NUM AS friendPhoneNum,  \n" +
                "\touyunc_im_user.ID_CARD_NUM AS friendIdCardNum, \n" +
                "\touyunc_im_user.AVATAR AS friendAvatar, \n" +
                "\touyunc_im_user.MOTTO AS friendMotto, \n" +
                "\touyunc_im_user.AGE AS friendAge, \n" +
                "\touyunc_im_user.SEX AS friendSex, \n" +
                "\touyunc_im_friend.IS_SHIELD AS friendIsShield, \n" +
                "\touyunc_im_friend.CREATE_TIME AS createTime, \n" +
                "\touyunc_im_friend.UPDATE_TIME AS updateTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_friend\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_friend.FRIEND_USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_friend.USER_ID = ? AND  ouyunc_im_friend.FRIEND_USER_ID = ?","获取用户好友信息"),

        SELECT_GROUP_USER("SELECT\n" +
                "\touyunc_im_group_user.GROUP_ID AS groupId, \n" +
                "\touyunc_im_group_user.USER_ID AS userId, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_group_user.USER_NICK_NAME AS userNickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum, \n" +
                "\touyunc_im_group_user.IS_LEADER AS isLeader, \n" +
                "\touyunc_im_group_user.IS_MANAGER AS isManager, \n" +
                "\touyunc_im_group_user.IS_SHIELD AS isShield, \n" +
                "\touyunc_im_group_user.MUSHIN, \n" +
                "\touyunc_im_group_user.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_group_user.USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_group_user.GROUP_ID = ? AND ouyunc_im_group_user.USER_ID = ? ","查询单个群成员"),

        SELECT_GROUP_LEADER_USER("SELECT\n" +
                "\touyunc_im_group_user.GROUP_ID AS groupId, \n" +
                "\touyunc_im_group_user.USER_ID AS userId, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_group_user.USER_NICK_NAME AS userNickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum, \n" +
                "\touyunc_im_group_user.IS_LEADER AS isLeader, \n" +
                "\touyunc_im_group_user.IS_MANAGER AS isManager, \n" +
                "\touyunc_im_group_user.IS_SHIELD AS isShield, \n" +
                "\touyunc_im_group_user.MUSHIN, \n" +
                "\touyunc_im_group_user.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_group_user.USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_group_user.GROUP_ID = ? AND ouyunc_im_group_user.IS_LEADER  = 1 ","查询单个群成员"),


        SELECT_GROUP_USERS("SELECT\n" +
                "\touyunc_im_group_user.GROUP_ID AS groupId, \n" +
                "\touyunc_im_group_user.USER_ID AS userId, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_group_user.USER_NICK_NAME AS userNickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum, \n" +
                "\touyunc_im_group_user.IS_LEADER AS isLeader, \n" +
                "\touyunc_im_group_user.IS_MANAGER AS isManager, \n" +
                "\touyunc_im_group_user.IS_SHIELD AS isShield, \n" +
                "\touyunc_im_group_user.MUSHIN, \n" +
                "\touyunc_im_group_user.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_group_user.USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_group_user.GROUP_ID = ?  ","查询群所有成员"),


        SELECT_GROUP_LEADER_USERS("SELECT\n" +
                "\touyunc_im_group_user.GROUP_ID AS groupId, \n" +
                "\touyunc_im_group_user.USER_ID AS userId, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_group_user.USER_NICK_NAME AS userNickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum, \n" +
                "\touyunc_im_group_user.IS_LEADER AS isLeader, \n" +
                "\touyunc_im_group_user.IS_MANAGER AS isManager, \n" +
                "\touyunc_im_group_user.IS_SHIELD AS isShield, \n" +
                "\touyunc_im_group_user.MUSHIN, \n" +
                "\touyunc_im_group_user.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "\tLEFT JOIN\n" +
                "\touyunc_im_group_user\n" +
                "\tON \n" +
                "\t\touyunc_im_user.ID = ouyunc_im_group_user.USER_ID\n" +
                "WHERE\n" +
                "\touyunc_im_user.DELETED = 0 AND\n" +
                "\touyunc_im_group_user.GROUP_ID = ?  AND (ouyunc_im_group_user.IS_LEADER  = 1 OR ouyunc_im_group_user.IS_MANAGER = 1) ","查询群管理员或群主列表"),

        SELECT_USER(" SELECT\n" +
                "\touyunc_im_user.ID, \n" +
                "\touyunc_im_user.OPEN_ID AS openId, \n" +
                "\touyunc_im_user.USERNAME, \n" +
                "\touyunc_im_user.APP_KEY AS appKey, \n" +
                "\touyunc_im_user.PASSWORD, \n" +
                "\touyunc_im_user.NICK_NAME AS nickName, \n" +
                "\touyunc_im_user.AVATAR, \n" +
                "\touyunc_im_user.MOTTO, \n" +
                "\touyunc_im_user.AGE, \n" +
                "\touyunc_im_user.SEX, \n" +
                "\touyunc_im_user.EMAIL, \n" +
                "\touyunc_im_user.STATUS, \n" +
                "\touyunc_im_user.PHONE_NUM AS phoneNum, \n" +
                "\touyunc_im_user.ID_CARD_NUM AS idCardNum, \n" +
                "\touyunc_im_user.FRIEND_JOIN_POLICY AS friendJoinPolicy,\n" +
                "\touyunc_im_user.GROUP_INVITE_POLICY AS groupInvitePolicy,\n" +
                "\touyunc_im_user.robot AS robot,\n" +
                "\touyunc_im_user.trusteeship AS trusteeship,\n" +

                "\touyunc_im_user.CREATE_TIME AS createTime, \n" +
                "\touyunc_im_user.UPDATE_TIME AS updateTime\n" +
                "FROM\n" +
                "\touyunc_im_user\n" +
                "WHERE\n" +
                "\tDELETED = 0 AND\n" +
                "\tID = ? ","查询用户信息"),

        SELECT_GROUP("SELECT \n" +
                "\touyunc_im_group.ID, \n" +
                "\touyunc_im_group.GROUP_NAME AS groupName, \n" +
                "\touyunc_im_group.APP_KEY AS appKey, \n" +
                "\touyunc_im_group.GROUP_AVATAR AS groupAvatar, \n" +
                "\touyunc_im_group.GROUP_DESCRIPTION AS groupDescription, \n" +
                "\touyunc_im_group.GROUP_ANNOUNCEMENT AS groupAnnouncement, \n" +
                "\touyunc_im_group.STATUS, \n" +
                "\touyunc_im_group.MUSHIN, \n" +
                "\touyunc_im_group.GROUP_JOIN_POLICY as groupJoinPolicy, \n" +
                "\touyunc_im_group.CREATE_TIME AS createTime, \n" +
                "\touyunc_im_group.UPDATE_TIME AS updateTime \n" +
                "FROM\n" +
                "\touyunc_im_group\n" +
                "WHERE\n" +
                "\tDELETED = 0 AND\n" +
                "\tID = ? ","查询群组信息"),

        SELECT_IM_APP_DETAIL("SELECT \n" +
                "\touyunc_im_app.ID, \n" +
                "\touyunc_im_app.APP_KEY as appKey, \n" +
                "\touyunc_im_app.APP_SECRET as appSecret, \n" +
                "\touyunc_im_app.APP_NAME as appName, \n" +
                "\touyunc_im_app.IM_MAX_CONNECTIONS as imMaxConnections, \n" +
                "\touyunc_im_app.CREATE_TIME as createTime, \n" +
                "\touyunc_im_app.UPDATE_TIME as updateTime, \n" +
                "\touyunc_im_app.DELETED\n" +
                "FROM\n" +
                "\touyunc_im_app\n" +
                "WHERE\n" +
                "\touyunc_im_app.DELETED = 0 AND\n" +
                "\touyunc_im_app.APP_KEY = ? ","查询IM APP DETAIL 详情信息"),

        SELECT_TIME_LINE("SELECT\n" +
                "ID,\n" +
                "PROTOCOL,\n" +
                "PROTOCOL_VERSION as protocolVersion,\n" +
                "DEVICE_TYPE as deviceType,\n" +
                "NETWORK_TYPE as networkType,\n" +
                "ENCRYPT_TYPE as encryptType,\n" +
                "SERIALIZE_ALGORITHM as serializeAlgorithm,\n" +
                "IP,\n" +
                "`FROM`,\n" +
                "`TO`,\n" +
                "TYPE,\n" +
                "CONTENT_TYPE as contentType,\n" +
                "CONTENT,\n" +
                "APP_KEY AS appKey, \n" +
                "EXTRA,\n" +
                "SEND_TIME as sendTime,\n" +
                "WITHDRAW,\n" +
                "CREATE_TIME as createTime,\n" +
                "UPDATE_TIME as updateTime,\n" +
                "DELETED\n" +
                "FROM\n" +
                "ouyunc_im_time_line\n" +
                "where id =  ? and deleted = 0","查询IM timeline 消息信箱的信息"),




        DELETE_GROUP("DELETE  FROM  ouyunc_im_group  WHERE ID = ? ","删除群"),
        DELETE_GROUP_USER("DELETE  FROM  ouyunc_im_group_user  WHERE GROUP_ID = ? AND USER_ID= ?","删除群某个成员关系"),
        DELETE_GROUP_ALL_USER("DELETE  FROM  ouyunc_im_group_user  WHERE GROUP_ID = ?","删除群所有成员关系"),


        UPDATE_TIME_LINE("UPDATE ouyunc_im_time_line SET withdraw = 1,  update_time = ? WHERE id = ? and deleted = 0" ,"撤销信箱箱的消息根据消息id"),

        INSERT_FRIEND("INSERT INTO ouyunc_im_friend (ID, USER_ID, FRIEND_USER_ID, FRIEND_NICK_NAME, IS_SHIELD, APP_KEY, CREATE_TIME, UPDATE_TIME) VALUES (?, ?, ?, ?, ?, ?,?, ?)","添加好友关系"),

        INSERT_GROUP_USER("INSERT INTO ouyunc_im_group_user (ID, GROUP_ID, USER_ID,GROUP_NICK_NAME, USER_NICK_NAME, IS_LEADER, IS_MANAGER, IS_SHIELD, MUSHIN, APP_KEY, CREATE_TIME) VALUES (?,?, ?,?, ?, ?, ?, ?, ?, ?, ?)","添加群成员关系"),

        INSERT_READ_RECEIPT("INSERT INTO ouyunc_im_read_receipt(ID, MSG_ID, USER_ID) VALUES (?, ?, ?)", "插入已读消息关系"),

        INSERT_TIME_LINE("INSERT INTO ouyunc_im_time_line (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT,EXTRA, SEND_TIME, WITHDRAW, APP_KEY, CREATE_TIME, UPDATE_TIME, DELETED) VALUES (?, ?, ? , ? ,? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "批量插入信箱箱消息（插入两条一条是自己的一条是别人的）"),

        INSERT_MESSAGE("INSERT INTO ouyunc_im_message (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT,EXTRA, SEND_TIME, APP_KEY, CREATE_TIME, UPDATE_TIME, DELETED) VALUES (?,?, ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入全局消息记录");



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
