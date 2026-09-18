package com.ouyunc.base.constant;

/**
 * 数据库脚本工具类,可自行扩展
 */
public class JdbcSqlConstant {

    /**
     * mysql 实现
     */
    public enum MYSQL{

        SELECT_MESSAGE("SELECT `id`, `protocol`, `protocol_version`, `device_type`, `network_type`, `encrypt_type`, `serialize_algorithm`, `message_type`, `retain`, `client_ip`, `message_id`, `from`, `from_type`, `to`, `to_type`, `content_type`, `content`, `extra`, `at`, `ref`, `correlation_id`, `app_key`, `qos`, `client_send_time`, `server_arrival_time` FROM `ouyunc_im_message` WHERE app_key = :app_key AND id IN (:ids)", "根据租户和主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("select app_key, `from`, device_type, `to`, `type`, `session_message_offset` from ouyunc_im_session_message_offset o where o.app_key = :app_key and o.`from` = :from and o.`to` = :to and o.`type` = :type and o.device_type = :device_type", "按租户主键获取会话偏移量"),

        SELECT_FRIEND("SELECT id, user_id, friend_user_code, friend_user_id, friend_nick_name, shield, way, channel, join_time, create_time, update_time FROM ouyunc_im_friend f where f.user_id = :user_id and f.friend_user_id = :friend_user_id and exists (select 1 from ouyunc_im_user u where u.id = f.user_id and u.app_key = :app_key and u.del_flag = 0)", "按租户查询好友关系"),
        SELECT_ALL_FRIEND("SELECT friend_user_id FROM ouyunc_im_friend f where f.user_id = :user_id and exists (select 1 from ouyunc_im_user u where u.id = f.user_id and u.app_key = :app_key and u.del_flag = 0) ORDER BY f.id LIMIT :limit", "按租户查询用户全部好友 id"),

        SELECT_GROUP("SELECT id,group_code,group_name,group_avatar,group_description,group_announcement,group_join_policy,`status`,silence,app_key,create_time,update_time,del_flag FROM ouyunc_im_group WHERE id = :id and app_key = :app_key and del_flag = 0 ", "按租户查询群组"),

        SELECT_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user gu WHERE gu.user_id = :user_id AND gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user gu WHERE gu.group_id = :group_id AND gu.user_id IN (:userIds) and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time  FROM ouyunc_im_group_user gu where gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户查询所有群成员"),
        COUNT_GROUP_USERS_BY_GROUP("SELECT COUNT(1) FROM ouyunc_im_group_user gu WHERE gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户统计群成员数"),
        COUNT_GROUPS_BY_USER("SELECT COUNT(1) FROM ouyunc_im_group_user gu WHERE gu.user_id = :user_id and exists (select 1 from ouyunc_im_user u where u.id = gu.user_id and u.app_key = :app_key and u.del_flag = 0)", "按租户统计用户加群数"),

        SELECT_USER("SELECT id,open_id, code, username,`password`,nick_name,avatar,motto,age,sex,email,phone_num,id_card_no,group_invite_policy,friend_join_policy,`status`,app_key,type,external_id,union_id,language,auto_translate_in,create_time,update_time,del_flag FROM ouyunc_im_user WHERE id = :id and app_key = :app_key and del_flag = 0", "按租户查询用户"),

        SELECT_BLACKLIST("select id, identity, user_id, identity_type, join_time, create_time from  ouyunc_im_blacklist where identity = :identity and user_id = :user_id and identity_type = :identity_type ", "查询黑名单"),

        SELECT_APP("SELECT id, app_key, app_secret, app_name, user_id, max_connections, `status`, create_time, update_time, del_flag FROM ouyunc_im_app WHERE app_key = :app_key AND del_flag = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT id, app_key, app_secret, app_name, user_id, max_connections, `status`, create_time, update_time, del_flag FROM ouyunc_im_app WHERE del_flag = 0", "查询全部未删除的 IM 应用"),

        INSERT_MQ_OUTBOX("INSERT INTO ouyunc_im_mq_outbox (id, topic, mq_key, biz_key, packet_id, payload, status, retry_count, next_retry_at, last_error, failure_context) VALUES (:id, :topic, :mq_key, :biz_key, :packet_id, :payload, :status, :retry_count, :next_retry_at, :last_error, :failure_context) ON DUPLICATE KEY UPDATE last_error = VALUES(last_error), failure_context = VALUES(failure_context), payload = VALUES(payload), packet_id = VALUES(packet_id), mq_key = VALUES(mq_key), retry_count = IF(status IN (:sending_status, :pending_status, :sent_status), retry_count, VALUES(retry_count)), next_retry_at = IF(status IN (:sending_status, :pending_status, :sent_status), next_retry_at, VALUES(next_retry_at)), status = IF(status IN (:sending_status, :sent_status), status, VALUES(status)), update_time = CURRENT_TIMESTAMP", "写入 MQ Outbox；SENDING/SENT 不覆盖状态，DEAD 才复活为 PENDING"),

        SELECT_MQ_OUTBOX_DUE("SELECT id, topic, mq_key, biz_key, packet_id, payload, status, retry_count, next_retry_at, last_error, failure_context, create_time, update_time FROM ouyunc_im_mq_outbox WHERE status = :status AND next_retry_at <= :now ORDER BY next_retry_at ASC LIMIT :limit", "扫描到期 PENDING Outbox"),

        CLAIM_MQ_OUTBOX("UPDATE ouyunc_im_mq_outbox SET status = :sending_status, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :pending_status", "认领 PENDING→SENDING"),

        MARK_SENT_MQ_OUTBOX("UPDATE ouyunc_im_mq_outbox SET status = :sent_status, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :sending_status", "补发成功先标 SENT，避免删行失败后被当成僵死 SENDING 复活"),

        DELETE_MQ_OUTBOX("DELETE FROM ouyunc_im_mq_outbox WHERE id = :id AND status = :sent_status", "删除已标 SENT 的 Outbox"),

        UPDATE_MQ_OUTBOX_RETRY("UPDATE ouyunc_im_mq_outbox SET status = :status, retry_count = :retry_count, next_retry_at = :next_retry_at, last_error = :last_error, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :sending_status", "Outbox 重试退避或置死信（仅 SENDING）"),

        RESET_STALE_MQ_OUTBOX_SENDING("UPDATE ouyunc_im_mq_outbox SET status = IF(retry_count + 1 >= :max_retry, :dead_status, :pending_status), next_retry_at = :now + LEAST(:backoff_max, :backoff_base << LEAST(retry_count + 1, 16)), last_error = :last_error, retry_count = retry_count + 1, update_time = CURRENT_TIMESTAMP WHERE status = :sending_status AND update_time < :stale_before", "回收超时 SENDING：累加 retry，超限置 DEAD")
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

        SELECT_MESSAGE("SELECT id, protocol, protocol_version, device_type, network_type, encrypt_type, serialize_algorithm, message_type, retain, client_ip, message_id, \"from\", from_type, \"to\", to_type, content_type, content, extra, \"at\", ref, correlation_id, app_key, qos, client_send_time, server_arrival_time FROM ouyunc_im_message WHERE app_key = :app_key AND id IN (:ids)", "根据租户和主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("select app_key, \"from\", device_type, \"to\", \"type\", session_message_offset from ouyunc_im_session_message_offset o where o.app_key = :app_key and o.\"from\" = :from and o.\"to\" = :to and o.\"type\" = :type and o.device_type = :device_type", "按租户主键获取会话偏移量"),

        SELECT_FRIEND("SELECT id, user_id, friend_user_code, friend_user_id, friend_nick_name, shield, way, channel, join_time, create_time, update_time FROM ouyunc_im_friend f where f.user_id = :user_id and f.friend_user_id = :friend_user_id and exists (select 1 from ouyunc_im_user u where u.id = f.user_id and u.app_key = :app_key and u.del_flag = 0)", "按租户查询好友关系"),
        SELECT_ALL_FRIEND("SELECT friend_user_id FROM ouyunc_im_friend f where f.user_id = :user_id and exists (select 1 from ouyunc_im_user u where u.id = f.user_id and u.app_key = :app_key and u.del_flag = 0) ORDER BY f.id LIMIT :limit", "按租户查询用户全部好友 id"),

        SELECT_GROUP("SELECT id,group_code,group_name,group_avatar,group_description,group_announcement,group_join_policy,\"status\",silence,app_key,create_time,update_time,del_flag FROM ouyunc_im_group WHERE id = :id and app_key = :app_key and del_flag = 0 ", "按租户查询群组"),

        SELECT_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user gu WHERE gu.user_id = :user_id AND gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time FROM ouyunc_im_group_user gu WHERE gu.group_id = :group_id AND gu.user_id IN (:userIds) and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT id, group_id, group_code, group_nick_name, user_id, user_code, post, user_nick_name, shield, silence, way, channel, create_time, join_time  FROM ouyunc_im_group_user gu where gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户查询所有群成员"),
        COUNT_GROUP_USERS_BY_GROUP("SELECT COUNT(1) FROM ouyunc_im_group_user gu WHERE gu.group_id = :group_id and exists (select 1 from ouyunc_im_group g where g.id = gu.group_id and g.app_key = :app_key and g.del_flag = 0)", "按租户统计群成员数"),
        COUNT_GROUPS_BY_USER("SELECT COUNT(1) FROM ouyunc_im_group_user gu WHERE gu.user_id = :user_id and exists (select 1 from ouyunc_im_user u where u.id = gu.user_id and u.app_key = :app_key and u.del_flag = 0)", "按租户统计用户加群数"),

        SELECT_USER("SELECT id,open_id, code, username,\"password\",nick_name,avatar,motto,age,sex,email,phone_num,id_card_no,group_invite_policy,friend_join_policy,\"status\",app_key,type,external_id,union_id,language,auto_translate_in,create_time,update_time,del_flag FROM ouyunc_im_user WHERE id = :id and app_key = :app_key and del_flag = 0", "按租户查询用户"),

        SELECT_BLACKLIST("select id, \"identity\", user_id, identity_type, join_time, create_time from  ouyunc_im_blacklist where \"identity\" = :identity and user_id = :user_id and identity_type = :identity_type ", "查询黑名单"),

        SELECT_APP("SELECT id, app_key, app_secret, app_name, user_id, max_connections, \"status\", create_time, update_time, del_flag FROM ouyunc_im_app WHERE app_key = :app_key AND del_flag = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT id, app_key, app_secret, app_name, user_id, max_connections, \"status\", create_time, update_time, del_flag FROM ouyunc_im_app WHERE del_flag = 0", "查询全部未删除的 IM 应用"),

        INSERT_MQ_OUTBOX("INSERT INTO ouyunc_im_mq_outbox (id, topic, mq_key, biz_key, packet_id, payload, status, retry_count, next_retry_at, last_error, failure_context) VALUES (:id, :topic, :mq_key, :biz_key, :packet_id, :payload, :status, :retry_count, :next_retry_at, :last_error, :failure_context) ON CONFLICT (topic, biz_key) DO UPDATE SET last_error = EXCLUDED.last_error, failure_context = EXCLUDED.failure_context, payload = EXCLUDED.payload, packet_id = EXCLUDED.packet_id, mq_key = EXCLUDED.mq_key, retry_count = CASE WHEN ouyunc_im_mq_outbox.status IN (:sending_status, :pending_status, :sent_status) THEN ouyunc_im_mq_outbox.retry_count ELSE EXCLUDED.retry_count END, next_retry_at = CASE WHEN ouyunc_im_mq_outbox.status IN (:sending_status, :pending_status, :sent_status) THEN ouyunc_im_mq_outbox.next_retry_at ELSE EXCLUDED.next_retry_at END, status = CASE WHEN ouyunc_im_mq_outbox.status IN (:sending_status, :sent_status) THEN ouyunc_im_mq_outbox.status ELSE EXCLUDED.status END, update_time = CURRENT_TIMESTAMP", "写入 MQ Outbox；SENDING/SENT 不覆盖状态，DEAD 才复活为 PENDING"),

        SELECT_MQ_OUTBOX_DUE("SELECT id, topic, mq_key, biz_key, packet_id, payload, status, retry_count, next_retry_at, last_error, failure_context, create_time, update_time FROM ouyunc_im_mq_outbox WHERE status = :status AND next_retry_at <= :now ORDER BY next_retry_at ASC LIMIT :limit", "扫描到期 PENDING Outbox"),

        CLAIM_MQ_OUTBOX("UPDATE ouyunc_im_mq_outbox SET status = :sending_status, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :pending_status", "认领 PENDING→SENDING"),

        MARK_SENT_MQ_OUTBOX("UPDATE ouyunc_im_mq_outbox SET status = :sent_status, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :sending_status", "补发成功先标 SENT，避免删行失败后被当成僵死 SENDING 复活"),

        DELETE_MQ_OUTBOX("DELETE FROM ouyunc_im_mq_outbox WHERE id = :id AND status = :sent_status", "删除已标 SENT 的 Outbox"),

        UPDATE_MQ_OUTBOX_RETRY("UPDATE ouyunc_im_mq_outbox SET status = :status, retry_count = :retry_count, next_retry_at = :next_retry_at, last_error = :last_error, update_time = CURRENT_TIMESTAMP WHERE id = :id AND status = :sending_status", "Outbox 重试退避或置死信（仅 SENDING）"),

        RESET_STALE_MQ_OUTBOX_SENDING("UPDATE ouyunc_im_mq_outbox SET status = CASE WHEN retry_count + 1 >= :max_retry THEN :dead_status ELSE :pending_status END, next_retry_at = :now + LEAST(:backoff_max, CAST(:backoff_base AS bigint) << LEAST(retry_count + 1, 16)), last_error = :last_error, retry_count = retry_count + 1, update_time = CURRENT_TIMESTAMP WHERE status = :sending_status AND update_time < :stale_before", "回收超时 SENDING：累加 retry，超限置 DEAD")
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

        SELECT_MESSAGE("SELECT ID, PROTOCOL, PROTOCOL_VERSION, DEVICE_TYPE, NETWORK_TYPE, ENCRYPT_TYPE, SERIALIZE_ALGORITHM, MESSAGE_TYPE, RETAIN, CLIENT_IP, MESSAGE_ID, \"FROM\", FROM_TYPE, \"TO\", TO_TYPE, CONTENT_TYPE, CONTENT, EXTRA, \"AT\", REF, CORRELATION_ID, APP_KEY, QOS, CLIENT_SEND_TIME, SERVER_ARRIVAL_TIME FROM OUYUNC_IM_MESSAGE WHERE APP_KEY = :app_key AND ID IN (:ids)", "根据租户和主键id查询消息"),

        SELECT_SESSION_MESSAGE_OFFSET("SELECT APP_KEY, \"FROM\", DEVICE_TYPE, \"TO\", \"TYPE\", SESSION_MESSAGE_OFFSET FROM OUYUNC_IM_SESSION_MESSAGE_OFFSET o WHERE o.APP_KEY = :app_key AND o.\"FROM\" = :from AND o.\"TO\" = :to AND o.\"TYPE\" = :type AND o.DEVICE_TYPE = :device_type", "按租户主键获取会话偏移量"),

        SELECT_FRIEND("SELECT ID, USER_ID, FRIEND_USER_CODE, FRIEND_USER_ID, FRIEND_NICK_NAME, SHIELD, WAY, CHANNEL, JOIN_TIME, CREATE_TIME, UPDATE_TIME FROM OUYUNC_IM_FRIEND f WHERE f.USER_ID = :user_id AND f.FRIEND_USER_ID = :friend_user_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_USER u WHERE u.ID = f.USER_ID AND u.APP_KEY = :app_key AND u.DELETED = 0)", "按租户查询好友关系"),
        SELECT_ALL_FRIEND("SELECT FRIEND_USER_ID FROM (SELECT FRIEND_USER_ID FROM OUYUNC_IM_FRIEND f WHERE f.USER_ID = :user_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_USER u WHERE u.ID = f.USER_ID AND u.APP_KEY = :app_key AND u.DELETED = 0) ORDER BY f.ID) WHERE ROWNUM <= :limit", "按租户查询用户全部好友 id"),

        SELECT_GROUP("SELECT ID, GROUP_CODE, GROUP_NAME, GROUP_AVATAR, GROUP_DESCRIPTION, GROUP_ANNOUNCEMENT, GROUP_JOIN_POLICY, STATUS, SILENCE, APP_KEY, CREATE_TIME, UPDATE_TIME, DELETED FROM OUYUNC_IM_GROUP WHERE ID = :id AND APP_KEY = :app_key AND DELETED = 0", "按租户查询群组"),

        SELECT_GROUP_USER("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER gu WHERE gu.USER_ID = :user_id AND gu.GROUP_ID = :group_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_GROUP g WHERE g.ID = gu.GROUP_ID AND g.APP_KEY = :app_key AND g.DELETED = 0)", "按租户查询群成员"),
        SELECT_GROUP_USER_BATCH("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER gu WHERE gu.GROUP_ID = :group_id AND gu.USER_ID IN (:userIds) AND EXISTS (SELECT 1 FROM OUYUNC_IM_GROUP g WHERE g.ID = gu.GROUP_ID AND g.APP_KEY = :app_key AND g.DELETED = 0)", "按租户批量查询群成员"),
        SELECT_ALL_GROUP_USER("SELECT ID, GROUP_ID, GROUP_CODE, GROUP_NICK_NAME, USER_ID, USER_CODE, POST, USER_NICK_NAME, SHIELD, SILENCE, WAY, CHANNEL, CREATE_TIME, JOIN_TIME FROM OUYUNC_IM_GROUP_USER gu WHERE gu.GROUP_ID = :group_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_GROUP g WHERE g.ID = gu.GROUP_ID AND g.APP_KEY = :app_key AND g.DELETED = 0)", "按租户查询所有群成员"),
        COUNT_GROUP_USERS_BY_GROUP("SELECT COUNT(1) FROM OUYUNC_IM_GROUP_USER gu WHERE gu.GROUP_ID = :group_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_GROUP g WHERE g.ID = gu.GROUP_ID AND g.APP_KEY = :app_key AND g.DELETED = 0)", "按租户统计群成员数"),
        COUNT_GROUPS_BY_USER("SELECT COUNT(1) FROM OUYUNC_IM_GROUP_USER gu WHERE gu.USER_ID = :user_id AND EXISTS (SELECT 1 FROM OUYUNC_IM_USER u WHERE u.ID = gu.USER_ID AND u.APP_KEY = :app_key AND u.DELETED = 0)", "按租户统计用户加群数"),

        SELECT_USER("SELECT ID, OPEN_ID, CODE, USERNAME, \"PASSWORD\", NICK_NAME, AVATAR, MOTTO, AGE, SEX, EMAIL, PHONE_NUM, ID_CARD_NO, GROUP_INVITE_POLICY, FRIEND_JOIN_POLICY, STATUS, APP_KEY, \"TYPE\", LANGUAGE, AUTO_TRANSLATE_IN, CREATE_TIME, UPDATE_TIME, DELETED FROM OUYUNC_IM_USER WHERE ID = :id AND APP_KEY = :app_key AND DELETED = 0", "按租户查询用户"),

        SELECT_BLACKLIST("SELECT ID, \"IDENTITY\", USER_ID, IDENTITY_TYPE, JOIN_TIME, CREATE_TIME FROM OUYUNC_IM_BLACKLIST WHERE \"IDENTITY\" = :identity AND USER_ID = :user_id AND IDENTITY_TYPE = :identity_type", "查询黑名单"),

        SELECT_APP("SELECT ID, APP_KEY, APP_SECRET, APP_NAME, USER_ID, MAX_CONNECTIONS, STATUS, CREATE_TIME, UPDATE_TIME, DELETED AS DEL_FLAG FROM OUYUNC_IM_APP WHERE APP_KEY = :app_key AND DELETED = 0", "根据 appKey 查询未删除的 IM 应用"),

        SELECT_ALL_APPS("SELECT ID, APP_KEY, APP_SECRET, APP_NAME, USER_ID, MAX_CONNECTIONS, STATUS, CREATE_TIME, UPDATE_TIME, DELETED AS DEL_FLAG FROM OUYUNC_IM_APP WHERE DELETED = 0", "查询全部未删除的 IM 应用"),

        INSERT_MQ_OUTBOX("MERGE INTO OUYUNC_IM_MQ_OUTBOX t USING (SELECT :id AS ID, :topic AS TOPIC, :mq_key AS MQ_KEY, :biz_key AS BIZ_KEY, :packet_id AS PACKET_ID, :payload AS PAYLOAD, :status AS STATUS, :retry_count AS RETRY_COUNT, :next_retry_at AS NEXT_RETRY_AT, :last_error AS LAST_ERROR, :failure_context AS FAILURE_CONTEXT FROM DUAL) s ON (t.TOPIC = s.TOPIC AND t.BIZ_KEY = s.BIZ_KEY) WHEN MATCHED THEN UPDATE SET t.LAST_ERROR = s.LAST_ERROR, t.FAILURE_CONTEXT = s.FAILURE_CONTEXT, t.PAYLOAD = s.PAYLOAD, t.PACKET_ID = s.PACKET_ID, t.MQ_KEY = s.MQ_KEY, t.RETRY_COUNT = CASE WHEN t.STATUS IN (:sending_status, :pending_status, :sent_status) THEN t.RETRY_COUNT ELSE s.RETRY_COUNT END, t.NEXT_RETRY_AT = CASE WHEN t.STATUS IN (:sending_status, :pending_status, :sent_status) THEN t.NEXT_RETRY_AT ELSE s.NEXT_RETRY_AT END, t.STATUS = CASE WHEN t.STATUS IN (:sending_status, :sent_status) THEN t.STATUS ELSE s.STATUS END, t.UPDATE_TIME = SYSTIMESTAMP WHEN NOT MATCHED THEN INSERT (ID, TOPIC, MQ_KEY, BIZ_KEY, PACKET_ID, PAYLOAD, STATUS, RETRY_COUNT, NEXT_RETRY_AT, LAST_ERROR, FAILURE_CONTEXT) VALUES (s.ID, s.TOPIC, s.MQ_KEY, s.BIZ_KEY, s.PACKET_ID, s.PAYLOAD, s.STATUS, s.RETRY_COUNT, s.NEXT_RETRY_AT, s.LAST_ERROR, s.FAILURE_CONTEXT)", "写入 MQ Outbox；SENDING/SENT 不覆盖状态，DEAD 才复活为 PENDING"),

        SELECT_MQ_OUTBOX_DUE("SELECT ID, TOPIC, MQ_KEY, BIZ_KEY, PACKET_ID, PAYLOAD, STATUS, RETRY_COUNT, NEXT_RETRY_AT, LAST_ERROR, FAILURE_CONTEXT, CREATE_TIME, UPDATE_TIME FROM (SELECT ID, TOPIC, MQ_KEY, BIZ_KEY, PACKET_ID, PAYLOAD, STATUS, RETRY_COUNT, NEXT_RETRY_AT, LAST_ERROR, FAILURE_CONTEXT, CREATE_TIME, UPDATE_TIME FROM OUYUNC_IM_MQ_OUTBOX WHERE STATUS = :status AND NEXT_RETRY_AT <= :now ORDER BY NEXT_RETRY_AT ASC) WHERE ROWNUM <= :limit", "扫描到期 PENDING Outbox"),

        CLAIM_MQ_OUTBOX("UPDATE OUYUNC_IM_MQ_OUTBOX SET STATUS = :sending_status, UPDATE_TIME = SYSTIMESTAMP WHERE ID = :id AND STATUS = :pending_status", "认领 PENDING→SENDING"),

        MARK_SENT_MQ_OUTBOX("UPDATE OUYUNC_IM_MQ_OUTBOX SET STATUS = :sent_status, UPDATE_TIME = SYSTIMESTAMP WHERE ID = :id AND STATUS = :sending_status", "补发成功先标 SENT，避免删行失败后被当成僵死 SENDING 复活"),

        DELETE_MQ_OUTBOX("DELETE FROM OUYUNC_IM_MQ_OUTBOX WHERE ID = :id AND STATUS = :sent_status", "删除已标 SENT 的 Outbox"),

        UPDATE_MQ_OUTBOX_RETRY("UPDATE OUYUNC_IM_MQ_OUTBOX SET STATUS = :status, RETRY_COUNT = :retry_count, NEXT_RETRY_AT = :next_retry_at, LAST_ERROR = :last_error, UPDATE_TIME = SYSTIMESTAMP WHERE ID = :id AND STATUS = :sending_status", "Outbox 重试退避或置死信（仅 SENDING）"),

        RESET_STALE_MQ_OUTBOX_SENDING("UPDATE OUYUNC_IM_MQ_OUTBOX SET STATUS = CASE WHEN RETRY_COUNT + 1 >= :max_retry THEN :dead_status ELSE :pending_status END, NEXT_RETRY_AT = :now + LEAST(:backoff_max, :backoff_base * POWER(2, LEAST(RETRY_COUNT + 1, 16))), LAST_ERROR = :last_error, RETRY_COUNT = RETRY_COUNT + 1, UPDATE_TIME = SYSTIMESTAMP WHERE STATUS = :sending_status AND UPDATE_TIME < :stale_before", "回收超时 SENDING：累加 retry，超限置 DEAD")
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
