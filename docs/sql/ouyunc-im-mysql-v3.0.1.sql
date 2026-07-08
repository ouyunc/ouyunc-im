/*
 Navicat Premium Data Transfer

 Source Server         : 本地
 Source Server Type    : MySQL
 Source Server Version : 50729
 Source Host           : localhost:3306
 Source Schema         : ouyunc-im

 Target Server Type    : MySQL
 Target Server Version : 50729
 File Encoding         : 65001

 Date: 02/01/2023 18:20:35
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for ouyunc_im_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_blacklist`;
CREATE TABLE `ouyunc_im_blacklist`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `identity` bigint(20) NULL DEFAULT NULL COMMENT '群或客户端唯一标识',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '客户端id',
  `identity_type` tinyint(1) NULL DEFAULT NULL COMMENT '唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_friend
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_friend`;
CREATE TABLE `ouyunc_im_friend`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户id',
  `friend_user_id` bigint(20) NULL DEFAULT NULL COMMENT '好友用户id',
  `friend_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '好友昵称',
  `is_shield` tinyint(1) NULL DEFAULT NULL COMMENT '是否屏蔽该好友，0-未屏蔽，1-屏蔽',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 好友表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_group
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_group`;
CREATE TABLE `ouyunc_im_group`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组名称',
  `group_avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组头像',
  `group_description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组描述',
  `group_announcement` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组公告',
  `status` tinyint(1) NULL DEFAULT NULL COMMENT '群状态，0-正常，1-异常（被平台封禁）',
  `mushin` tinyint(1) NULL DEFAULT NULL COMMENT '是否全体禁言（群主和管理员除外），0-不禁言，1-禁言',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '群信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_group_user
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_group_user`;
CREATE TABLE `ouyunc_im_group_user`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `group_id` bigint(20) NULL DEFAULT NULL COMMENT '群组id',
  `user_id` bigint(20) NULL DEFAULT NULL COMMENT '用户id',
  `is_leader` tinyint(1) NULL DEFAULT NULL COMMENT '是否是群主，0-否，1-是',
  `is_manager` tinyint(1) NULL DEFAULT NULL COMMENT '是否是群管理员，0-否，1-是',
  `user_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户昵称（用户在群里的昵称）',
  `is_shield` tinyint(1) NULL DEFAULT NULL COMMENT '是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽',
  `mushin` tinyint(1) NULL DEFAULT NULL COMMENT '用户在群中的状态，0-未被禁言，1-被禁言',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '群成员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_message
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_message`;
CREATE TABLE `ouyunc_im_message`  (
  `id` bigint(20) NOT NULL COMMENT '主键消息id',
  `protocol` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NULL DEFAULT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NULL DEFAULT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NULL DEFAULT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NULL DEFAULT NULL COMMENT '消息内容序列化算法',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送者ip',
  `from` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息发送者',
  `to` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `type` tinyint(4) NULL DEFAULT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `content_type` tinyint(4) NULL DEFAULT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `send_time` bigint(20) NULL DEFAULT NULL COMMENT '消息发送时间戳',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 消息全量存储表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_read_receipt
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_read_receipt`;
CREATE TABLE `ouyunc_im_read_receipt`  (
  `id` bigint(20) NOT NULL COMMENT '主键',
  `msg_id` bigint(20) NULL DEFAULT NULL COMMENT '消息id，',
  `user_id` int(11) NULL DEFAULT NULL COMMENT '已读消息的用户id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_receive_message
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_receive_message`;
CREATE TABLE `ouyunc_im_receive_message`  (
  `id` bigint(20) NOT NULL COMMENT '主键消息id',
  `protocol` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NULL DEFAULT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NULL DEFAULT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NULL DEFAULT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NULL DEFAULT NULL COMMENT '消息内容序列化算法',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送者ip',
  `from` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息发送者',
  `to` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `type` tinyint(4) NULL DEFAULT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `content_type` tinyint(4) NULL DEFAULT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `receive_time` bigint(20) NULL DEFAULT NULL COMMENT '消息接收时间',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 消息收件箱' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_send_message
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_send_message`;
CREATE TABLE `ouyunc_im_send_message`  (
  `id` bigint(20) NOT NULL COMMENT '主键消息id',
  `protocol` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NULL DEFAULT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NULL DEFAULT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NULL DEFAULT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NULL DEFAULT NULL COMMENT '消息内容序列化算法',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送者ip',
  `from` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息发送者',
  `to` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `type` tinyint(4) NULL DEFAULT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `content_type` tinyint(4) NULL DEFAULT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `send_time` bigint(20) NULL DEFAULT NULL COMMENT '消息发送时间戳',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 消息发件箱' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_user
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_user`;
CREATE TABLE `ouyunc_im_user`  (
  `id` bigint(20) NOT NULL COMMENT '主键id',
  `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '开放id',
  `username` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名称（对应于身份证）',
  `password` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户名密码',
  `nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户别名',
  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户头像url',
  `motto` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '座右铭/格言',
  `age` tinyint(1) NULL DEFAULT NULL COMMENT '年龄',
  `sex` tinyint(1) NULL DEFAULT NULL COMMENT '性别：0-女，1-男，2-其他',
  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '邮箱',
  `phone_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '手机号（国内）',
  `id_card_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '身份证号码',
  `status` tinyint(1) NULL DEFAULT NULL COMMENT '用户状态：0-正常，1-异常（被平台封禁）',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 用户表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
