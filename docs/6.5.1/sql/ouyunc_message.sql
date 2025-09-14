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
DROP TABLE IF EXISTS `ouyunc_im_app`;
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
DROP TABLE IF EXISTS `ouyunc_im_blacklist`;
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
DROP TABLE IF EXISTS `ouyunc_im_friend`;
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
DROP TABLE IF EXISTS `ouyunc_im_group`;
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
DROP TABLE IF EXISTS `ouyunc_im_group_user`;
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
DROP TABLE IF EXISTS `ouyunc_im_message`;
CREATE TABLE `ouyunc_im_message`  (
  `id` bigint(20) NOT NULL COMMENT '主键消息id (packetId)',
  `protocol` tinyint(1) NOT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NOT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NOT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NOT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NOT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NOT NULL COMMENT '消息内容序列化算法',
  `message_type` tinyint(1) NOT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `retain` tinyint(1) NOT NULL DEFAULT 0 COMMENT '保留位，1个字节',
  `client_ip` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端ip',
  `from` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息发送者',
  `to` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `content_type` int(11) NOT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用唯一标识',
  `extra` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容扩展字段',
  `at` json NULL COMMENT '@功能，这里存放客户端登录绑定的id',
  `qos` tinyint(1) NOT NULL COMMENT '消息可靠性标识 qos = 0/1/2\r\n     * QoS 0：至多一次，at most once；发送方发送一条消息，接收方最多能接收到一次。即发送方完成消息发送之后不关心消息发送是否成功。\r\n     * QoS 1：至少一次，at least once；发送方发送一条消息，接收方至少能接收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接受方接收到消息为止。这种模式下可能会导致接收方收到重复的消息。\r\n     * QoS 2：确保一次：exactly once；发送方发送一条消息，接收方一定且只能收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接收方接收到消息为止，在这一过程中同时保证接收方不会因为消息重传而收到重复的消息。',
  `client_send_time` bigint(20) NOT NULL COMMENT '消息发送时间戳',
  `server_arrival_time` bigint(20) NOT NULL COMMENT '消息首次到达服务端时间戳',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '默认删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `idx_from_to`(`from`, `to`) USING BTREE COMMENT 'from_to组合索引',
  INDEX `idx_message_type`(`message_type`) USING BTREE COMMENT '消息类型索引',
  INDEX `idx_client_send_time`(`client_send_time`) USING BTREE COMMENT '客户端发送时间索引',
  INDEX `idx_server_arrival_time`(`server_arrival_time`) USING BTREE COMMENT '服务端到达时间索引',
  INDEX `idx_to`(`to`) USING BTREE COMMENT 'to索引',
  INDEX `idx_message_content_type`(`content_type`) USING BTREE COMMENT '消息内容类型索引',
  INDEX `idx_app_key`(`app_key`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 消息业务全量存储表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of ouyunc_im_message
-- ----------------------------
INSERT INTO `ouyunc_im_message` VALUES (402646592778571776, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.1.5', '1391066871210487808', '1391067238606352384', -128, '你好，我想加你为好友', 'ouyunc', NULL, NULL, 0, 1752066414450, 1752066414448, '2025-07-09 21:06:58', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402647093855293440, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.1.5', '1391066871210487808', '1391067238606352384', -128, '赶紧同意啊', 'ouyunc', NULL, NULL, 0, 1752066474012, 1752066474008, '2025-07-09 21:07:54', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402647330569228288, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '我来发起你同意', 'ouyunc', NULL, NULL, 0, 1752066502289, 1752066502282, '2025-07-09 21:08:22', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402647545300815872, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '你怎么不同意了', 'ouyunc', NULL, NULL, 0, 1752066527885, 1752066527872, '2025-07-09 21:08:47', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402665295876362240, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391066871210487808', '1391067238606352384', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068643856, 1752068643856, '2025-07-09 21:44:03', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402665618300899328, 1, 1, 11, 0, 0, 6, -10, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '拒绝好友请求', 'ouyunc', NULL, NULL, 0, 1752068682328, 1752068682315, '2025-07-09 21:44:42', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666186557788160, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068750227, 1752068750224, '2025-07-09 21:45:50', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666203418890240, 1, 1, 11, 0, 0, 6, -10, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '拒绝好友请求', 'ouyunc', NULL, NULL, 0, 1752068752199, 1752068752191, '2025-07-09 21:45:52', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666220414210048, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068754203, 1752068754193, '2025-07-09 21:45:54', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666236688109568, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068756035, 1752068756026, '2025-07-09 21:45:56', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666242413334528, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068756888, 1752068756882, '2025-07-09 21:45:56', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666298608619520, 1, 1, 11, 0, 0, 6, -9, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '同意好友请求', 'ouyunc', NULL, NULL, 0, 1752068763630, 1752068763626, '2025-07-09 21:46:03', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402666326895005696, 1, 1, 11, 0, 0, 6, -10, 0, '192.168.1.5', '1391067238606352384', '1391066871210487808', -128, '拒绝好友请求', 'ouyunc', NULL, NULL, 0, 1752068766790, 1752068766786, '2025-07-09 21:46:06', b'0');
INSERT INTO `ouyunc_im_message` VALUES (402673039899529216, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.1.5', '1391066871210487808', '1391067238606352384', -128, '再次添加好友', 'ouyunc', NULL, NULL, 0, 1752069567039, 1752069567037, '2025-07-09 21:59:27', b'0');
INSERT INTO `ouyunc_im_message` VALUES (404634401890537472, 1, 1, 11, 0, 0, 6, -12, 0, '192.168.31.143', '1391067238606352384', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"邀请你加入群聊\\\"麻子的群\\\"\"}', 'ouyunc', NULL, NULL, 0, 1752303379698, 1752303379698, '2025-07-12 14:56:23', b'0');
INSERT INTO `ouyunc_im_message` VALUES (404636421582131200, 1, 1, 11, 0, 0, 6, -12, 0, '192.168.31.143', '1391067238606352384', '1393607921930080256', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"邀请你加入群聊\\\"第三个群\\\"\"}', 'ouyunc', NULL, NULL, 0, 1752303620589, 1752303620576, '2025-07-12 15:00:20', b'0');
INSERT INTO `ouyunc_im_message` VALUES (404639516479361024, 1, 1, 11, 0, 0, 6, -12, 0, '192.168.31.143', '1391067238606352384', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"邀请你加入群聊\\\"大沙发大概\\\"\"}', 'ouyunc', NULL, NULL, 0, 1752303989319, 1752303989313, '2025-07-12 15:06:29', b'0');
INSERT INTO `ouyunc_im_message` VALUES (404805613132353536, 1, 1, 11, 0, 0, 6, -12, 0, '192.168.31.143', '1391067238606352384', '1393692517610819584', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"邀请你加入群聊\\\"新建的群组\\\"\"}', 'ouyunc', NULL, NULL, 0, 1752323789783, 1752323789772, '2025-07-12 20:36:32', b'0');
INSERT INTO `ouyunc_im_message` VALUES (404806346351218688, 1, 1, 11, 0, 0, 6, -12, 0, '192.168.31.143', '1391067238606352384', '1393692883643535360', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"邀请你加入群聊\\\"dfg\\\"\"}', 'ouyunc', NULL, NULL, 0, 1752323877044, 1752323877040, '2025-07-12 20:37:57', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057053306163200, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737949502, 1754737949503, '2025-08-09 19:12:32', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057137938829312, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737959440, 1754737959441, '2025-08-09 19:12:39', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057155861090304, 1, 1, 11, 0, 0, 6, -16, 0, '192.168.31.144', '1391066871210487808', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"拒绝邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737961665, 1754737961651, '2025-08-09 19:12:41', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057165377966080, 1, 1, 11, 0, 0, 6, -16, 0, '192.168.31.144', '1391066871210487808', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"拒绝邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737962910, 1754737962897, '2025-08-09 19:12:42', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057169400303616, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393606911367057408', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737963357, 1754737963348, '2025-08-09 19:12:43', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057176681615360, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737964069, 1754737964063, '2025-08-09 19:12:44', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057182704635904, 1, 1, 11, 0, 0, 6, -16, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"拒绝邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737964993, 1754737964987, '2025-08-09 19:12:45', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057189822369792, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737965666, 1754737965660, '2025-08-09 19:12:45', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057194079588352, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737966169, 1754737966169, '2025-08-09 19:12:46', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057195576954880, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737966526, 1754737966522, '2025-08-09 19:12:46', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057198542327808, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737966721, 1754737966720, '2025-08-09 19:12:46', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057199838367744, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737967030, 1754737967027, '2025-08-09 19:12:47', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057203541938176, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737967401, 1754737967395, '2025-08-09 19:12:47', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057207178399744, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737967756, 1754737967750, '2025-08-09 19:12:47', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057208730292224, 1, 1, 11, 0, 0, 6, -15, 0, '192.168.31.144', '1391066871210487808', '1393609468499988480', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意邀请加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737968126, 1754737968117, '2025-08-09 19:12:48', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057284441673728, 1, 1, 11, 0, 0, 6, -13, 0, '192.168.31.144', '1391067238606352384', '1393692517610819584', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意wanger加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737976961, 1754737976955, '2025-08-09 19:12:56', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057293052579840, 1, 1, 11, 0, 0, 6, -14, 0, '192.168.31.144', '1391067238606352384', '1393692517610819584', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"拒绝wanger加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737977990, 1754737977976, '2025-08-09 19:12:58', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425057297930555392, 1, 1, 11, 0, 0, 6, -13, 0, '192.168.31.144', '1391067238606352384', '1393692517610819584', -8, '{\"identity\":\"1391066871210487808\",\"content\":\"同意wanger加入群聊\"}', 'ouyunc', NULL, NULL, 0, 1754737978641, 1754737978628, '2025-08-09 19:12:58', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425060046227279872, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.31.144', '1391066871210487808', '1391067238606352384', -128, '你好，我想加你为好友', 'ouyunc', NULL, NULL, 0, 1754738306206, 1754738306197, '2025-08-09 19:18:26', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425066479853408256, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.31.144', '1391066871210487808', '1391067238606352384', -128, '赶快同意啊', 'ouyunc', NULL, NULL, 0, 1754739073126, 1754739073119, '2025-08-09 19:31:38', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425067090439213056, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.31.144', '1391066871210487808', '1391067238606352384', -128, '我发第二次了，赶快同意', 'ouyunc', NULL, NULL, 0, 1754739145997, 1754739145997, '2025-08-09 19:33:02', b'0');
INSERT INTO `ouyunc_im_message` VALUES (425067485924331520, 1, 1, 11, 0, 0, 6, -8, 0, '192.168.31.144', '1391066871210487808', '1391067238606352384', -128, '你个吊毛', 'ouyunc', NULL, NULL, 0, 1754739193184, 1754739193180, '2025-08-09 19:33:22', b'0');

-- ----------------------------
-- Table structure for ouyunc_im_message_withdraw
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_message_withdraw`;
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
DROP TABLE IF EXISTS `ouyunc_im_session_message_offset`;
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
DROP TABLE IF EXISTS `ouyunc_im_user`;
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

SET FOREIGN_KEY_CHECKS = 1;
