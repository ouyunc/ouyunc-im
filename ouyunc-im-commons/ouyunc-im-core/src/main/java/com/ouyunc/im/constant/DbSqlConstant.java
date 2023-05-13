package com.ouyunc.im.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class DbSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{


        SELECT_MESSAGE_READ_RECEIPT("SELECT READ_LIST AS readList, \n" +
                "\tID, \n" +
                "\tPROTOCOL, \n" +
                "\tPROTOCOL_VERSION AS protocolVersion, \n" +
                "\tDEVICE_TYPE AS deviceType, \n" +
                "\tNETWORK_TYPE AS networkType, \n" +
                "\tENCRYPT_TYPE AS encryptType, \n" +
                "\tSERIALIZE_ALGORITHM  AS serializeAlgorithm, \n" +
                "\tIP, \n" +
                "\tFROM, \n" +
                "\tTO, \n" +
                "\tTYPE, \n" +
                "\tCONTENT_TYPE AS contentType, \n" +
                "\tCONTENT, \n" +
                "\tSEND_TIME AS sendTime, \n" +
                "\tCREATE_TIME AS createTime, \n" +
                "\tUPDATE_TIME AS updateTime \n" +
                "\t FROM OUYUNC_IM_SEND_MESSAGE WHERE DELETED = 0 AND ID = ? LIMIT 1","查询已读列表"),


        SELECT_BLACK_LIST("\tSELECT\n" +
                "\tOUYUNC_IM_BLACKLIST.IDENTITY, \n" +
                "\tOUYUNC_IM_BLACKLIST.USER_ID AS userId, \n" +
                "\tOUYUNC_IM_BLACKLIST.IDENTITY_TYPE AS identityType, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_USER.NICK_NAME AS nickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum,\n" +
                "\tOUYUNC_IM_BLACKLIST.CREATE_TIME AS createTime\n" +
                "\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_BLACKLIST\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_BLACKLIST.USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_BLACKLIST.IDENTITY = ? AND\n" +
                "\tOUYUNC_IM_BLACKLIST.IDENTITY_TYPE = ? AND\n" +
                "\tOUYUNC_IM_BLACKLIST.USER_ID = ? ", "获取黑名单的用户信息"),

        SELECT_FRIEND_USER("SELECT\n" +
                "OUYUNC_IM_FRIEND.USER_ID AS  userId,\n" +
                "OUYUNC_IM_FRIEND.FRIEND_USER_ID AS friendUserId,\n" +
                "\tOUYUNC_IM_USER.USERNAME AS FRIENDUSERNAME, \n" +
                "\tOUYUNC_IM_FRIEND.FRIEND_NICK_NAME AS friendNickName, \n" +
                "\tOUYUNC_IM_USER.EMAIL AS friendEmail, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS friendPhoneNum,  \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS friendIdCardNum, \n" +
                "\tOUYUNC_IM_USER.AVATAR AS friendAvatar, \n" +
                "\tOUYUNC_IM_USER.MOTTO AS friendMotto, \n" +
                "\tOUYUNC_IM_USER.AGE AS friendAge, \n" +
                "\tOUYUNC_IM_USER.SEX AS friendSex, \n" +
                "\tOUYUNC_IM_FRIEND.IS_SHIELD AS friendIsShield, \n" +
                "\tOUYUNC_IM_FRIEND.CREATE_TIME AS createTime, \n" +
                "\tOUYUNC_IM_FRIEND.UPDATE_TIME AS updateTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_FRIEND\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_FRIEND.FRIEND_USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_FRIEND.USER_ID = ? AND  OUYUNC_IM_FRIEND.FRIEND_USER_ID = ?","获取用户好友信息"),

        SELECT_GROUP_USER("SELECT\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID AS groupId, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_ID AS userId, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_NICK_NAME AS userNickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_LEADER AS isLeader, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_MANAGER AS isManager, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_SHIELD AS isShield, \n" +
                "\tOUYUNC_IM_GROUP_USER.MUSHIN, \n" +
                "\tOUYUNC_IM_GROUP_USER.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_GROUP_USER\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_GROUP_USER.USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID = ? AND OUYUNC_IM_GROUP_USER.USER_ID = ? ","查询单个群成员"),

        SELECT_GROUP_LEADER_USER("SELECT\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID AS groupId, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_ID AS userId, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_NICK_NAME AS userNickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_LEADER AS isLeader, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_MANAGER AS isManager, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_SHIELD AS isShield, \n" +
                "\tOUYUNC_IM_GROUP_USER.MUSHIN, \n" +
                "\tOUYUNC_IM_GROUP_USER.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_GROUP_USER\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_GROUP_USER.USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID = ? AND OUYUNC_IM_GROUP_USER.IS_LEADER  = 1 ","查询单个群成员"),


        SELECT_GROUP_USERS("SELECT\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID AS groupId, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_ID AS userId, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_NICK_NAME AS userNickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_LEADER AS isLeader, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_MANAGER AS isManager, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_SHIELD AS isShield, \n" +
                "\tOUYUNC_IM_GROUP_USER.MUSHIN, \n" +
                "\tOUYUNC_IM_GROUP_USER.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_GROUP_USER\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_GROUP_USER.USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID = ?  ","查询群所有成员"),


        SELECT_GROUP_LEADER_USERS("SELECT\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID AS groupId, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_ID AS userId, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_GROUP_USER.USER_NICK_NAME AS userNickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_LEADER AS isLeader, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_MANAGER AS isManager, \n" +
                "\tOUYUNC_IM_GROUP_USER.IS_SHIELD AS isShield, \n" +
                "\tOUYUNC_IM_GROUP_USER.MUSHIN, \n" +
                "\tOUYUNC_IM_GROUP_USER.CREATE_TIME AS createTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "\tLEFT JOIN\n" +
                "\tOUYUNC_IM_GROUP_USER\n" +
                "\tON \n" +
                "\t\tOUYUNC_IM_USER.ID = OUYUNC_IM_GROUP_USER.USER_ID\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_USER.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_GROUP_USER.GROUP_ID = ?  AND (OUYUNC_IM_GROUP_USER.IS_LEADER  = 1 OR OUYUNC_IM_GROUP_USER.IS_MANAGER = 1) ","查询群管理员或群主列表"),

        SELECT_USER(" SELECT\n" +
                "\tOUYUNC_IM_USER.ID, \n" +
                "\tOUYUNC_IM_USER.OPEN_ID AS openId, \n" +
                "\tOUYUNC_IM_USER.USERNAME, \n" +
                "\tOUYUNC_IM_USER.PASSWORD, \n" +
                "\tOUYUNC_IM_USER.NICK_NAME AS nickName, \n" +
                "\tOUYUNC_IM_USER.AVATAR, \n" +
                "\tOUYUNC_IM_USER.MOTTO, \n" +
                "\tOUYUNC_IM_USER.AGE, \n" +
                "\tOUYUNC_IM_USER.SEX, \n" +
                "\tOUYUNC_IM_USER.EMAIL, \n" +
                "\tOUYUNC_IM_USER.STATUS, \n" +
                "\tOUYUNC_IM_USER.PHONE_NUM AS phoneNum, \n" +
                "\tOUYUNC_IM_USER.ID_CARD_NUM AS idCardNum, \n" +
                "\tOUYUNC_IM_USER.FRIEND_ANSWER_POLICY AS friendAnswerPolicy,\n" +
                "\tOUYUNC_IM_USER.GROUP_ANSWER_POLICY AS groupAnswerPolicy,\n" +

                "\tOUYUNC_IM_USER.CREATE_TIME AS createTime, \n" +
                "\tOUYUNC_IM_USER.UPDATE_TIME AS updateTime\n" +
                "FROM\n" +
                "\tOUYUNC_IM_USER\n" +
                "WHERE\n" +
                "\tDELETED = 0 AND\n" +
                "\tID = ? ","查询用户信息"),

        SELECT_GROUP("SELECT \n" +
                "\tOUYUNC_IM_GROUP.ID, \n" +
                "\tOUYUNC_IM_GROUP.GROUP_NAME AS groupName, \n" +
                "\tOUYUNC_IM_GROUP.GROUP_AVATAR AS groupAvatar, \n" +
                "\tOUYUNC_IM_GROUP.GROUP_DESCRIPTION AS groupDescription, \n" +
                "\tOUYUNC_IM_GROUP.GROUP_ANNOUNCEMENT AS groupAnnouncement, \n" +
                "\tOUYUNC_IM_GROUP.STATUS, \n" +
                "\tOUYUNC_IM_GROUP.MUSHIN, \n" +
                "\tOUYUNC_IM_GROUP.CREATE_TIME AS createTime, \n" +
                "\tOUYUNC_IM_GROUP.UPDATE_TIME AS updateTime \n" +
                "FROM\n" +
                "\tOUYUNC_IM_GROUP\n" +
                "WHERE\n" +
                "\tDELETED = 0 AND\n" +
                "\tID = ? ","查询群组信息"),

        SELECT_IM_APP_DETAIL("SELECT \n" +
                "\tOUYUNC_IM_APP_DETAIL.ID, \n" +
                "\tOUYUNC_IM_APP_DETAIL.APP_KEY as appKey, \n" +
                "\tOUYUNC_IM_APP_DETAIL.APP_SECRET, \n" +
                "\tOUYUNC_IM_APP_DETAIL.APP_NAME as appName, \n" +
                "\tOUYUNC_IM_APP_DETAIL.IM_MAX_CONNECTIONS as imMaxConnections, \n" +
                "\tOUYUNC_IM_APP_DETAIL.CREATE_TIME as createTime, \n" +
                "\tOUYUNC_IM_APP_DETAIL.UPDATE_TIME as updateTime, \n" +
                "\tOUYUNC_IM_APP_DETAIL.DELETED\n" +
                "FROM\n" +
                "\tOUYUNC_IM_APP_DETAIL\n" +
                "WHERE\n" +
                "\tOUYUNC_IM_APP_DETAIL.DELETED = 0 AND\n" +
                "\tOUYUNC_IM_APP_DETAIL.APP_KEY = ? ","查询IM APP DETAIL 详情信息"),

        DELETE_GROUP("DELETE  FROM  OUYUNC_IM_GROUP  WHERE ID = ? ","删除群"),
        DELETE_GROUP_USER("DELETE  FROM  OUYUNC_IM_GROUP_USER  WHERE GROUP_ID = ? AND USER_ID= ?","删除群某个成员关系"),
        DELETE_GROUP_ALL_USER("DELETE  FROM  OUYUNC_IM_GROUP_USER  WHERE GROUP_ID = ?","删除群所有成员关系"),


        UPDATE_MESSAGE_READ_RECEIPT("UPDATE OUYUNC_IM_SEND_MESSAGE SET READ_LIST = ? WHERE DELETED = 0 AND ID = ? ","读已回执，更新已读列表"),


        INSERT_FRIEND("INSERT INTO OUYUNC_IM_FRIEND (ID, USER_ID, FRIEND_USER_ID, FRIEND_NICK_NAME, IS_SHIELD, CREATE_TIME, UPDATE_TIME) VALUES (?, ?, ?, ?, ?, ?, ?)","添加好友关系"),

        INSERT_GROUP_USER("INSERT INTO OUYUNC_IM_GROUP_USER (ID, GROUP_ID, USER_ID,GROUP_NICK_NAME, USER_NICK_NAME, IS_LEADER, IS_MANAGER, IS_SHIELD, MUSHIN, CREATE_TIME) VALUES (?, ?,?, ?, ?, ?, ?, ?, ?, ?)","添加群成员关系"),

        INSERT_READ_RECEIPT("INSERT INTO OUYUNC_IM_READ_RECEIPT(ID, MSG_ID, USER_ID) VALUES (?, ?, ?)", "插入已读消息关系"),

        INSERT_SEND_MESSAGE("INSERT INTO OUYUNC_IM_SEND_MESSAGE (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT, SEND_TIME, CREATE_TIME, UPDATE_TIME, DELETED) VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入发件箱消息"),

        INSERT_RECEIVE_MESSAGE("INSERT INTO OUYUNC_IM_RECEIVE_MESSAGE (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT, RECEIVE_TIME, CREATE_TIME, UPDATE_TIME, DELETED) VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入收件箱消息"),

        INSERT_MESSAGE("INSERT INTO OUYUNC_IM_MESSAGE (ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, IP, `FROM`, `TO`, TYPE, CONTENT_TYPE, CONTENT, SEND_TIME, CREATE_TIME, UPDATE_TIME, DELETED) VALUES ( ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , ? , 0)", "插入消息"),

        INSERT_BLACK_LIST("INSERT INTO OUYUNC_IM_BLACKLIST (ID, IDENTITY, USER_ID, IDENTITY_TYPE, CREATE_TIME) VALUES (?, ?, ?, ?, ?) ", "将个人或群加入黑名单");


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
