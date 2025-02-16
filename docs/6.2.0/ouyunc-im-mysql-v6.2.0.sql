/*
 Navicat Premium Data Transfer

 Source Server         : 本地
 Source Server Type    : MySQL
 Source Server Version : 50729
 Source Host           : localhost:3306
 Source Schema         : ouyunc-message

 Target Server Type    : MySQL
 Target Server Version : 50729
 File Encoding         : 65001

 Date: 16/02/2025 17:31:56
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

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
                                      `to` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息接收者/群组',
                                      `content_type` int(10) NOT NULL COMMENT '消息内容类型: 文本，图片，音频...',
                                      `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息内容',
                                      `extra` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容扩展字段',
                                      `at` json NULL COMMENT '@功能，这里存放客户端登录绑定的id',
                                      `qos` tinyint(1) NOT NULL COMMENT '消息可靠性标识 qos = 0/1/2\r\n     * QoS 0：至多一次，at most once；发送方发送一条消息，接收方最多能接收到一次。即发送方完成消息发送之后不关心消息发送是否成功。\r\n     * QoS 1：至少一次，at least once；发送方发送一条消息，接收方至少能接收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接受方接收到消息为止。这种模式下可能会导致接收方收到重复的消息。\r\n     * QoS 2：确保一次：exactly once；发送方发送一条消息，接收方一定且只能收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接收方接收到消息为止，在这一过程中同时保证接收方不会因为消息重传而收到重复的消息。',
                                      `client_send_time` bigint(20) NOT NULL COMMENT '消息发送时间戳',
                                      `server_arrival_time` bigint(20) NOT NULL COMMENT '消息首次到达服务端时间戳',
                                      `read` tinyint(1) NOT NULL DEFAULT 0 COMMENT '已读（只针对单聊有效，该字段对群聊业务无效）：0-未读，1-已读,  2-已读撤回（未读）',
                                      `withdrawn` bit(1) NOT NULL DEFAULT b'0' COMMENT '已撤回：0-未撤回，1-已撤回',
                                      `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                      `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                      `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '默认删除，1-已删除，0-未删除',
                                      PRIMARY KEY (`id`) USING BTREE,
                                      INDEX `idx_from_to`(`from`, `to`) USING BTREE COMMENT 'from_to组合索引',
                                      INDEX `idx_message_type`(`message_type`) USING BTREE COMMENT '消息类型索引',
                                      INDEX `idx_client_send_time`(`client_send_time`) USING BTREE COMMENT '客户端发送时间索引',
                                      INDEX `idx_server_arrival_time`(`server_arrival_time`) USING BTREE COMMENT '服务端到达时间索引',
                                      INDEX `idx_to`(`to`) USING BTREE COMMENT 'to索引',
                                      INDEX `idx_message_content_type`(`content_type`) USING BTREE COMMENT '消息内容类型索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 消息业务全量存储表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_read_receipt
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_read_receipt`;
CREATE TABLE `ouyunc_im_read_receipt`  (
                                           `id` bigint(20) NOT NULL COMMENT '主键',
                                           `msg_id` bigint(20) NOT NULL COMMENT '消息id,(packetId)',
                                           `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '已读消息的用户id',
                                           `read_time` bigint(20) NOT NULL COMMENT '已读时间戳',
                                           `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                           PRIMARY KEY (`id`) USING BTREE,
                                           INDEX `idx_msg_id`(`msg_id`) USING BTREE COMMENT '消息id'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息已读回执' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
