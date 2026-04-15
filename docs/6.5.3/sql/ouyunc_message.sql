/*
 Navicat Premium Data Transfer

 Source Server         : 本地
 Source Server Type    : MySQL
 Source Server Version : 50729
 Source Host           : localhost:3306
 Source Schema         : ouyunc_message

 Target Server Type    : MySQL
 Target Server Version : 50729
 File Encoding         : 65001

 Date: 14/09/2025 15:24:50
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ouyunc_im_app
-- ----------------------------

CREATE TABLE `ouyunc_im_app`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `app_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端（外部平台）key  唯一',
  `app_secret` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端 （外部平台）secret',
  `app_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '客户端 （外部平台）name',
  `user_id` bigint(20) NOT NULL COMMENT '用户id，一般是企业的账户',
  `max_connections` bigint(20) NOT NULL DEFAULT 0 COMMENT 'IM 最大连接数 大于等于-1： -1 - 无限制，',
  `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '1-有效，2-禁用/锁定/无效',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_key`(`app_key`) USING BTREE,
  INDEX `idx_user_id_app_key`(`user_id`, `app_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 应用配置' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ouyunc_im_app
-- ----------------------------
INSERT INTO `ouyunc_im_app` VALUES (1, 'ouyunc', '123456', '偶云客', 1, 0, 1, '2025-07-05 10:50:06', '2025-07-05 10:50:06', 0);

-- ----------------------------
-- Table structure for ouyunc_im_blacklist
-- ----------------------------

CREATE TABLE `ouyunc_im_blacklist`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `identity` bigint(20) NOT NULL COMMENT '群或客户端唯一标识',
  `user_id` bigint(20) NOT NULL COMMENT '客户端id（被加入identity 黑名单）',
  `identity_type` tinyint(1) NOT NULL COMMENT '唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `join_time` bigint(20) NULL DEFAULT NULL COMMENT '加入黑名单时间戳，毫秒',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `identity_userId`(`identity`, `user_id`) USING BTREE COMMENT '关系唯一索引',
  INDEX `identity_userId_type`(`identity`, `user_id`, `identity_type`) USING BTREE COMMENT '联合索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '黑名单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_friend
-- ----------------------------

CREATE TABLE `ouyunc_im_friend`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户id',
  `friend_user_id` bigint(20) NULL DEFAULT NULL COMMENT '好友用户id',
  `friend_user_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '好友用户code',
  `friend_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '好友昵称',
  `shield` tinyint(1) NULL DEFAULT 0 COMMENT '是否屏蔽该好友，0-未屏蔽，1-屏蔽',
  `join_time` bigint(20) NOT NULL COMMENT '成为好友的时间戳',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_friend_user_id`(`user_id`, `friend_user_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '好友表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_group
-- ----------------------------

CREATE TABLE `ouyunc_im_group`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `group_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '群号',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组名称',
  `group_avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组头像',
  `group_description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组描述',
  `group_announcement` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组公告',
  `group_join_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群加入策略：0-加群需要验证，1-加群自动同意',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '群状态，1-正常，2-异常（被平台封禁）',
  `silence` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否全体禁言（群主和管理员除外），0-不禁言，1-禁言',
  `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用appKey',
  `create_time` datetime NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_app_key_group_name`(`app_key`, `group_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '群信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_group_user
-- ----------------------------

CREATE TABLE `ouyunc_im_group_user`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `group_id` bigint(20) NOT NULL COMMENT '群组id',
  `group_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组code',
  `group_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组别名（该用户对这个群起的别名）',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户id',
  `user_code` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户code',
  `user_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户昵称（用户在群里的昵称）',
  `post` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群职位，0-普通成员，1-管理员，2-群主',
  `shield` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽',
  `silence` tinyint(1) NOT NULL DEFAULT 0 COMMENT '用户在群中的状态，0-未被禁言，1-被禁言',
  `join_time` bigint(20) NOT NULL COMMENT '加入时间',
  `way` tinyint(4) NOT NULL DEFAULT 1 COMMENT '加群方式：1-主动加群，2-被动加群（被邀请），3-扫码加群  ......',
  `channel` tinyint(4) NOT NULL DEFAULT 1 COMMENT '加群渠道，预留，默认1',
  `group_offset` int(11) NOT NULL COMMENT '在群中的偏移量，加群时就已经确定，也就是排序，后续可以用做bit map',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `group_user_id`(`user_id`, `group_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '群成员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_message
-- ----------------------------

CREATE TABLE `ouyunc_im_message` (
                                     `id` bigint(20) NOT NULL COMMENT '主键消息id (packetId),服务端生成',
                                     `protocol` tinyint(1) NOT NULL COMMENT '消息协议类型',
                                     `protocol_version` tinyint(1) NOT NULL COMMENT '消息协议版本号',
                                     `device_type` tinyint(1) NOT NULL COMMENT '设备类型',
                                     `network_type` tinyint(1) NOT NULL COMMENT '网络类型',
                                     `encrypt_type` tinyint(1) NOT NULL COMMENT '消息加密算法',
                                     `serialize_algorithm` tinyint(1) NOT NULL COMMENT '消息内容序列化算法',
                                     `message_type` tinyint(1) NOT NULL COMMENT '消息类型：心跳，群聊，私聊...',
                                     `retain` tinyint(1) NOT NULL DEFAULT '0' COMMENT '保留位，1个字节',
                                     `client_ip` varchar(40) NOT NULL COMMENT '客户端ip',
                                     `message_id` varchar(64) NOT NULL COMMENT '客户端消息id',
                                     `from` varchar(64) NOT NULL COMMENT '消息发送者',
                                     `from_type` tinyint(1) NOT NULL COMMENT '发送方类型',
                                     `to` varchar(64) DEFAULT NULL COMMENT '消息接收者/群组',
                                     `to_type` tinyint(1) NOT NULL COMMENT '接收方类型',
                                     `content_type` int(11) NOT NULL COMMENT '消息内容类型: 文本，图片，音频...',
                                     `content` varchar(2000) DEFAULT NULL COMMENT '消息内容',
                                     `app_key` varchar(64) NOT NULL COMMENT '应用唯一标识',
                                     `extra` varchar(2000) DEFAULT NULL COMMENT '消息内容扩展字段',
                                     `at` json DEFAULT NULL COMMENT '@功能，这里存放客户端登录绑定的id',
                                     `qos` tinyint(1) NOT NULL COMMENT '消息可靠性标识 qos = 0/1/2\r\n     * QoS 0：至多一次，at most once；发送方发送一条消息，接收方最多能接收到一次。即发送方完成消息发送之后不关心消息发送是否成功。\r\n     * QoS 1：至少一次，at least once；发送方发送一条消息，接收方至少能接收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接受方接收到消息为止。这种模式下可能会导致接收方收到重复的消息。\r\n     * QoS 2：确保一次：exactly once；发送方发送一条消息，接收方一定且只能收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接收方接收到消息为止，在这一过程中同时保证接收方不会因为消息重传而收到重复的消息。',
                                     `client_send_time` bigint(20) NOT NULL COMMENT '消息发送时间戳',
                                     `server_arrival_time` bigint(20) NOT NULL COMMENT '消息首次到达服务端时间戳',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '默认删除，1-已删除，0-未删除',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     KEY `idx_message_type` (`message_type`) USING BTREE COMMENT '消息类型索引',
                                     KEY `idx_client_send_time` (`client_send_time`) USING BTREE COMMENT '客户端发送时间索引',
                                     KEY `idx_server_arrival_time` (`server_arrival_time`) USING BTREE COMMENT '服务端到达时间索引',
                                     KEY `idx_message_content_type` (`content_type`) USING BTREE COMMENT '消息内容类型索引',
                                     KEY `idx_app_key_from_to` (`app_key`,`from`,`to`,`from_type`,`to_type`) USING BTREE COMMENT 'from_to组合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='im 消息业务全量存储表';

-- ----------------------------
-- Table structure for ouyunc_im_message_withdraw
-- ----------------------------

CREATE TABLE `ouyunc_im_message_withdraw`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `withdrawn_time` bigint(20) NOT NULL COMMENT '撤回时间戳，单位毫秒',
  `withdraw_user_id` bigint(20) NOT NULL COMMENT '撤回人id',
  `device_type` tinyint(1) NOT NULL COMMENT '撤回人所登录设备',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息撤回表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_session_message_offset
-- ----------------------------

CREATE TABLE `ouyunc_im_session_message_offset`  (
  `from` bigint(20) NOT NULL COMMENT '发送方ID',
  `to` bigint(20) NOT NULL COMMENT '接收方ID（用户或群组）',
  `type` tinyint(1) NOT NULL COMMENT '会话类型：1-一对一，2-群',
  `session_message_offset` bigint(20) NOT NULL DEFAULT 0 COMMENT '会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取',
  `device_type` tinyint(1) NOT NULL COMMENT '发送方所登录设备类型',
  PRIMARY KEY (`from`, `to`, `type`, `device_type`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'ouyunc_im_group_user 或 ouyunc_im_friend 会话消息偏移量\r\n建议使用该方式进行频繁的更新\r\nINSERT INTO ouyunc_im_session_message_offset (`from`, `to`, `type`, `session_message_offset`)\r\nVALUES (?, ?, ?, ?)\r\nON DUPLICATE KEY UPDATE session_message_offset = VALUES(session_message_offset);' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ouyunc_im_session_message_offset
-- ----------------------------
INSERT INTO `ouyunc_im_session_message_offset` VALUES (1, 1, 1, 0, 0);

-- ----------------------------
-- Table structure for ouyunc_im_user
-- ----------------------------

CREATE TABLE `ouyunc_im_user`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '开放id',
  `code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT 'OO号',
  `username` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名称（对应于身份证）',
  `password` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名密码',
  `nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户别名',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户头像url',
  `motto` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '座右铭/格言',
  `age` tinyint(1) NULL DEFAULT NULL COMMENT '年龄',
  `sex` tinyint(1) NULL DEFAULT NULL COMMENT '性别：0-女，1-男，2-其他',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号（国内）',
  `id_card_no` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '身份证号码',
  `group_invite_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群邀请的应答策略：0-需要验证，1-自动通过',
  `friend_join_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '好友添加的应答策略：0-需要验证，1-自动通过',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '用户状态：1-正常，2-异常（被平台封禁）',
  `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用appKey',
  `robot` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否是机器人：0-不是，1-是',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `idx_open_id`(`open_id`) USING BTREE,
  INDEX `idx_app_key`(`app_key`) USING BTREE,
  INDEX `idx_email`(`email`) USING BTREE,
  INDEX `idx_phone_num`(`phone_num`) USING BTREE,
  INDEX `idx_id_card_num`(`id_card_no`) USING BTREE,
  INDEX `idx_app_key_username`(`app_key`, `username`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ouyunc_im_user
-- ----------------------------
INSERT INTO `ouyunc_im_user` VALUES (1, '111', '166500152', 'user', '$2a$10$gc2IZ1zfvkC90V51ahqvA.BWnN7LdwfcWcWoHSkWfhV7.8HNE6kcG', '张三', NULL, NULL, NULL, NULL, NULL, NULL, NULL, 0, 0, 1, 'ouyunc', 0, '2025-06-26 21:38:29', '2025-07-06 10:38:07', 0);
INSERT INTO `ouyunc_im_user` VALUES (1389380225893183488, '22', '666500152', '方振勋', '$2a$10$wBVQU9Rzh6UUqoj/5Swv/OGX6NA7QKodevk54cEFQB8tU4EIC39r6', '方振勋', NULL, NULL, NULL, NULL, '664936598@qq.com', NULL, NULL, 0, 0, 1, 'ouyunc', 0, '2025-06-30 23:00:59', '2025-07-06 10:38:13', 0);
INSERT INTO `ouyunc_im_user` VALUES (1391066871210487808, '1391066869910253568', '966500152', 'wanger', '$2a$10$tAfZ2ciTH/fVyp2.Uvk2P.Xf6fR7yDlQy/hl96aPbwB2bYhLs5uri', 'wanger', NULL, NULL, NULL, NULL, '12@qq.com', NULL, NULL, 0, 0, 1, 'ouyunc', 0, '2025-07-05 14:43:06', '2025-07-05 14:43:06', 0);
INSERT INTO `ouyunc_im_user` VALUES (1391067238606352384, '1391067238576992256', '600445851', 'mazi', '$2a$10$w5E/PFWSIQhWPCkf4cCoGuesXTHIO8.hn0Q7F8ecr7oaoe58i4R6.', 'mazi', NULL, NULL, NULL, NULL, '665@qq.com', NULL, NULL, 0, 0, 1, 'ouyunc', 0, '2025-07-05 14:44:34', '2025-07-06 10:42:12', 0);




-- ----------------------------
-- Table structure for ouyunc_im_consultation_ticket
-- ----------------------------
-- ----------------------------
-- Table structure for ouyunc_im_consultation_ticket
-- ----------------------------
CREATE TABLE `ouyunc_im_consultation_ticket` (
                                                 `id` bigint(20) NOT NULL COMMENT '主键id',
                                                 `app_key` varchar(64) NOT NULL COMMENT '应用 appKey',
                                                 `session_id` varchar(128) NOT NULL COMMENT 'IM 会话 id，即 IdentityUtil.sessionId(用户id, 客服id)',
                                                 `user_id` varchar(64) NOT NULL COMMENT '客户用户 id（IM 中的 identity）',
                                                 `service_identity` varchar(64) NOT NULL COMMENT '客服侧 id（虚拟客服 id）',
                                                 `status` tinyint(4) NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，2-已关闭，3-已转接（仅记录，当前接待以 assignee 为准）',
                                                 `assignee_id` varchar(64) NULL DEFAULT NULL COMMENT '当前接待的客服人员 id（坐席 identity），转接时更新',
                                                 `assignee_name` varchar(64) NULL DEFAULT NULL COMMENT '当前接待客服昵称/工号，冗余便于展示',
                                                 `is_robot` tinyint(1) NULL DEFAULT 0 COMMENT '当前是否机器人接待：0-人工 1-机器人',
                                                 `channel` varchar(32) NULL DEFAULT NULL COMMENT '进线渠道：APP、MINIAPP、H5、WEB、PC',
                                                 `queue_id` varchar(64) NULL DEFAULT NULL COMMENT '来源队列ID（如 app123_pre_sale）',
                                                 `queue_in_time` datetime NULL DEFAULT NULL COMMENT '进入队列时间',
                                                 `queue_status` tinyint(4) NULL DEFAULT 0 COMMENT '排队状态：0-未排队 1-已分配 2-超时',
                                                 `start_time` datetime NULL DEFAULT NULL COMMENT '本次咨询开始时间（进线时间）',
                                                 `end_time` datetime NULL DEFAULT NULL COMMENT '本次咨询结束时间（关单时间）',
                                                 `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注（如关单原因、转接原因）',
                                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                                 `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
                                                 PRIMARY KEY (`id`) USING BTREE,
                                                 INDEX `idx_app_key` (`app_key`) USING BTREE,
                                                 INDEX `idx_session_id` (`app_key`, `session_id`) USING BTREE COMMENT '按会话查该会话下所有咨询单',
                                                 INDEX `idx_user_id` (`app_key`, `user_id`) USING BTREE COMMENT '按客户查其历史咨询单',
                                                 INDEX `idx_assignee_status` (`app_key`, `assignee_id`, `status`) USING BTREE COMMENT '按坐席与状态查待处理/进行中',
                                                 INDEX `idx_start_time` (`app_key`, `start_time`) USING BTREE COMMENT '按进线时间范围查询'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '咨询单表（客服多次进线拆分维度）' ROW_FORMAT = Dynamic;
-- ----------------------------
-- 咨询记录表（咨询单流转明细：每次转接/接入/关单生成一条，用于时间线与处理人维度查询）
-- ----------------------------
-- ----------------------------
-- 咨询记录表（咨询单流转明细：每次转接/接入/关单生成一条，用于时间线与处理人维度查询）
-- ----------------------------
CREATE TABLE `ouyunc_im_consultation_ticket_log` (
                                                     `id` bigint(20) NOT NULL COMMENT '主键id',
                                                     `app_key` varchar(64) NOT NULL COMMENT '应用 appKey',
                                                     `ticket_id` bigint(20) NOT NULL COMMENT '咨询单 id，关联 ouyunc_im_consultation_ticket.id',
                                                     `record_type` tinyint(4) NOT NULL COMMENT '记录类型：1-首次接入，2-转接转入，3-转出（结束本次接待），4-关单',
                                                     `assignee_id` varchar(64) NOT NULL COMMENT '本段处理人 id（坐席 identity）',
                                                     `assignee_name` varchar(64) NULL DEFAULT NULL COMMENT '本段处理人昵称/工号',
                                                     `is_robot` tinyint(1) NULL DEFAULT 0 COMMENT '本段接待是否机器人：0-人工 1-机器人',
                                                     `satisfaction` tinyint(4) NULL DEFAULT NULL COMMENT '本段满意度：1-非常不满意 2-不满意 3-一般 4-满意 5-非常满意',
                                                     `start_time` bigint(20) NOT NULL COMMENT '本段开始时间，毫秒时间戳',
                                                     `end_time` bigint(20) NULL DEFAULT NULL COMMENT '本段结束时间（转出或关单时填），毫秒时间戳',
                                                     `from_assignee_id` varchar(64) NULL DEFAULT NULL COMMENT '转接时：转出方坐席 id（record_type=2 时可选填）',
                                                     `from_assignee_name` varchar(64) NULL DEFAULT NULL COMMENT '转接时：转出方坐席名称',
                                                     `to_assignee_id` varchar(64) NULL DEFAULT NULL COMMENT '转接时：转入方坐席 id（record_type=3 时可选填）',
                                                     `to_assignee_name` varchar(64) NULL DEFAULT NULL COMMENT '转接时：转入方坐席名称',
                                                     `remark` varchar(500) NULL DEFAULT NULL COMMENT '备注（转接原因、关单原因等）',
                                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                                     `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
                                                     PRIMARY KEY (`id`) USING BTREE,
                                                     INDEX `idx_ticket_id` (`app_key`, `ticket_id`) USING BTREE COMMENT '按咨询单查流转时间线',
                                                     INDEX `idx_assignee_time` (`app_key`, `assignee_id`, `start_time`) USING BTREE COMMENT '按处理人+时间查其接待记录',
                                                     INDEX `idx_start_time` (`app_key`, `start_time`) USING BTREE COMMENT '按时间范围统计'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '咨询记录表（咨询单流转，每次转接/接入/关单一条）' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
