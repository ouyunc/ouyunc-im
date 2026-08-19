/*
 客服（CS）模块 — 完整建表脚本（合并所有增量）
 Target Schema: ouyunc_cs
 库边界：ouyunc_system（用户）/ ouyunc_message（IM）/ ouyunc_cs（客服）
 跨库约定：cs_agent.agent_id = ouyunc_system.sys_user.id（逻辑关联）
 MySQL 8.0+ / utf8mb4_0900_ai_ci
 表数量：18（登录走 OAuth system-user，无 cs_agent_credential）
*/

CREATE DATABASE IF NOT EXISTS `ouyunc_cs`
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE `ouyunc_cs`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- =============================================================================
-- 1. 客户 / 商户
-- =============================================================================

CREATE TABLE IF NOT EXISTS `cs_customer` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户 appKey',
    `customer_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户 IM identity，租户内唯一',
    `customer_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户昵称',
    `customer_type` tinyint NOT NULL DEFAULT 2 COMMENT '1-注册用户 2-游客（服务端生成 identity）',
    `member_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '业务会员 ID',
    `device_sn` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '设备号 SN（客户端上报，仅统计/展示）',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次进线渠道',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_customer` (`app_key`, `customer_id`) USING BTREE,
    UNIQUE KEY `uk_app_member` (`app_key`, `member_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客服客户主数据' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_merchant` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户 appKey',
    `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家业务 ID，租户内唯一',
    `merchant_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家名称',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_merchant` (`app_key`, `merchant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客服商家主数据' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_merchant_entry` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户 appKey',
    `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '商家 ID',
    `entry_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入口编码，如 main/luxury',
    `entry_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '入口展示名',
    `service_identity` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'IM 虚拟客服 identity，租户内唯一',
    `is_default` tinyint NOT NULL DEFAULT 0 COMMENT '是否默认入口：1-是',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '1-启用 0-停用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_merchant_entry` (`app_key`, `merchant_id`, `entry_code`) USING BTREE,
    UNIQUE KEY `uk_app_service_identity` (`app_key`, `service_identity`) USING BTREE,
    INDEX `idx_app_merchant` (`app_key`, `merchant_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '商家 IM 入口' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 2. 咨询单 / 流转
-- =============================================================================

CREATE TABLE IF NOT EXISTS `cs_consultation_ticket` (
    `id` bigint(20) NOT NULL COMMENT '主键id',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用 appKey',
    `session_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'IM 会话 id，IdentityUtil.sessionId(用户id, 客服id)',
    `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户用户 id（IM identity）',
    `user_name` varchar(128) NULL DEFAULT NULL COMMENT '客户昵称',
    `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商家 ID',
    `entry_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IM 入口编码',
    `user_mobile` varchar(32) NULL DEFAULT NULL COMMENT '客户手机号',
    `user_email` varchar(128) NULL DEFAULT NULL COMMENT '客户邮箱',
    `country_code` char(2) NULL DEFAULT NULL COMMENT 'ISO3166 国家二字码',
    `phone_country_prefix` varchar(8) NULL DEFAULT NULL COMMENT '国际电话区号',
    `preferred_language` varchar(16) NULL DEFAULT NULL COMMENT '客户偏好语言 BCP47：zh-CN/en-US',
    `service_identity` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客服侧虚拟客服id',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，2-已关闭，3-已转接',
    `service_state` tinyint NOT NULL DEFAULT 1 COMMENT '1=ACTIVE 2=HOSTED 3=PENDING_RESUME',
    `hosting_mode` tinyint NOT NULL DEFAULT 0 COMMENT '0=无 1=访客idle 2=坐席SLA 3=手动',
    `hosting_reason` varchar(64) NULL DEFAULT NULL COMMENT '托管原因',
    `hosted_at` datetime NULL DEFAULT NULL COMMENT '进入托管时间',
    `last_activity_at` datetime NULL DEFAULT NULL COMMENT '最后活动时间',
    `idle_strike` int NOT NULL DEFAULT 0 COMMENT '空闲计数',
    `pending_resume` tinyint NOT NULL DEFAULT 0 COMMENT '待恢复标记',
    `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前接待坐席id',
    `assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前接待客服昵称/工号',
    `origin_assignee_id` varchar(64) NULL DEFAULT NULL COMMENT '首次分配坐席ID',
    `transfer_count` tinyint NOT NULL DEFAULT 0 COMMENT '转接次数',
    `is_robot` tinyint NULL DEFAULT 0 COMMENT '0-人工 1-机器人',
    `is_robot_transfer` tinyint NOT NULL DEFAULT 0 COMMENT '是否机器人转人工',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '渠道实例：whatsapp_a / im / 400_call',
    `channel_type` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '协议：whatsapp/telegram/line/im/400_call',
    `terminal_type` varchar(16) NULL DEFAULT NULL COMMENT 'APP/MINIAPP/H5/WEB/PC（channel_type=im）',
    `client_ip` varchar(64) NULL DEFAULT NULL COMMENT '客户真实IP',
    `device_info` varchar(256) NULL DEFAULT NULL COMMENT '设备信息',
    `referrer_url` varchar(512) NULL DEFAULT NULL COMMENT '访客来源页面',
    `queue_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源队列/技能编码',
    `queue_in_time` datetime NULL DEFAULT NULL COMMENT '进入队列时间',
    `queue_wait_second` int NULL DEFAULT 0 COMMENT '排队等待时长(秒)',
    `queue_status` tinyint NULL DEFAULT 0 COMMENT '0-未排队 1-已分配 2-超时',
    `start_time` datetime NULL DEFAULT NULL COMMENT '进线时间',
    `end_time` datetime NULL DEFAULT NULL COMMENT '关单时间',
    `consult_duration_second` int NULL DEFAULT 0 COMMENT '咨询总时长(秒)',
    `close_type` tinyint NULL DEFAULT NULL COMMENT '1客户 2坐席 3超时 4转接 5访客idle 6托管超时',
    `biz_order_no` varchar(128) NULL DEFAULT NULL COMMENT '关联业务订单号',
    `member_id` varchar(64) NULL DEFAULT NULL COMMENT '业务会员ID',
    `biz_tag` varchar(256) NULL DEFAULT NULL COMMENT '业务标签，逗号分隔',
    `satisfaction_score` tinyint NULL DEFAULT NULL COMMENT '1差评 2一般 3好评',
    `satisfaction_comment` varchar(500) NULL DEFAULT NULL COMMENT '客户评价内容',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    `active_guard` tinyint GENERATED ALWAYS AS (IF(`del_flag` = 0 AND `status` = 1, 1, NULL)) STORED COMMENT '进行中占位：1=占用唯一槽',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_key` (`app_key`) USING BTREE,
    INDEX `idx_app_session` (`app_key`, `session_id`) USING BTREE,
    INDEX `idx_app_user` (`app_key`, `user_id`) USING BTREE,
    UNIQUE INDEX `uk_app_user_service_active` (`app_key`, `user_id`, `service_identity`, `active_guard`) USING BTREE COMMENT '同一客户在同一 IM 入口仅一条进行中工单',
    INDEX `idx_app_merchant_user` (`app_key`, `merchant_id`, `user_id`) USING BTREE,
    INDEX `idx_app_assignee_status` (`app_key`, `assignee_id`, `status`) USING BTREE,
    INDEX `idx_app_start_time` (`app_key`, `start_time`) USING BTREE,
    INDEX `idx_app_channel` (`app_key`, `channel`) USING BTREE,
    INDEX `idx_app_biz_order` (`app_key`, `biz_order_no`) USING BTREE,
    INDEX `idx_app_country` (`app_key`, `country_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '咨询单表' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_consultation_ticket_log` (
    `id` bigint(20) NOT NULL COMMENT '主键id',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用 appKey',
    `ticket_id` bigint(20) NOT NULL COMMENT '咨询单 id',
    `session_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'IM会话ID',
    `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户IM身份ID',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '进线渠道',
    `record_type` tinyint NOT NULL COMMENT '1首次 2转入 3转出 4关单 5托管 6恢复 7idle提示 8SLA升级',
    `operate_type` tinyint NULL DEFAULT NULL COMMENT '1正常 2主动转接 3超时 4离线 5强制关单',
    `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本段处理人 id',
    `assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本段处理人昵称',
    `is_robot` tinyint NULL DEFAULT 0 COMMENT '0-人工 1-机器人',
    `bot_flow_id` varchar(64) NULL DEFAULT NULL COMMENT '机器人流程ID',
    `transfer_mode` tinyint NULL DEFAULT NULL COMMENT '1手动 2系统',
    `from_assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `from_assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `to_assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `to_assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `from_queue_id` varchar(64) NULL DEFAULT NULL,
    `to_queue_id` varchar(64) NULL DEFAULT NULL,
    `queue_wait_second` int NULL DEFAULT 0,
    `handle_duration_second` int NULL DEFAULT 0,
    `satisfaction` tinyint NULL DEFAULT NULL COMMENT '1-5 分制',
    `satisfaction_comment` varchar(500) NULL DEFAULT NULL,
    `start_time` bigint(20) NOT NULL COMMENT '毫秒时间戳',
    `end_time` bigint(20) NULL DEFAULT NULL COMMENT '毫秒时间戳',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_ticket` (`app_key`, `ticket_id`) USING BTREE,
    INDEX `idx_app_assignee_time` (`app_key`, `assignee_id`, `start_time`) USING BTREE,
    INDEX `idx_app_assignee_channel` (`app_key`, `assignee_id`, `channel`) USING BTREE,
    INDEX `idx_app_user` (`app_key`, `user_id`) USING BTREE,
    INDEX `idx_app_start_time` (`app_key`, `start_time`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '咨询记录表（流转明细）' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_work_order` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `ticket_id` bigint(20) DEFAULT NULL COMMENT '关联咨询单',
    `customer_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci,
    `order_type` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'follow_up' COMMENT 'follow_up/refund/complaint',
    `status` tinyint NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已完成 3已关闭',
    `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `creator_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_ticket` (`app_key`, `ticket_id`) USING BTREE,
    INDEX `idx_app_customer` (`app_key`, `customer_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '业务工单（跟进单）' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 3. 坐席 / 路由 / 排班
-- =============================================================================

CREATE TABLE IF NOT EXISTS `cs_agent` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key',
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席标识，等于 sys_user.id（字符串）',
    `agent_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席昵称/姓名',
    `job_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '员工工号',
    `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `agent_type` tinyint NOT NULL DEFAULT 1 COMMENT '1人工 2机器人 3虚拟客服',
    `max_concurrent` int NOT NULL DEFAULT 5 COMMENT '最大并发接待会话数',
    `max_hosted_ratio` decimal(4, 2) NULL DEFAULT NULL COMMENT '覆盖平台托管上限比例',
    `max_hosted_absolute` int NULL DEFAULT NULL COMMENT '覆盖平台托管绝对上限',
    `languages` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `receive_channel` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `is_auto_distribute` tinyint NOT NULL DEFAULT 1 COMMENT '是否系统自动分配',
    `enabled` tinyint(1) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_agent` (`app_key`, `agent_id`) USING BTREE,
    INDEX `idx_app_jobno` (`app_key`, `job_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席静态配置' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_skill` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '技能编码=队列编码',
    `skill_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `max_queue_num` int NOT NULL DEFAULT 20,
    `queue_timeout_second` int NOT NULL DEFAULT 300,
    `overflow_strategy` tinyint NOT NULL DEFAULT 1 COMMENT '1转兜底 2自动关闭 3保留等待',
    `overflow_skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `priority` int NOT NULL DEFAULT 10,
    `status` tinyint(1) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_skill` (`app_key`, `skill_code`) USING BTREE,
    INDEX `idx_app_name` (`app_key`, `skill_name`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技能/队列配置' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_skill_rule` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `rule_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `sort` int NOT NULL DEFAULT 1,
    `allow_channel` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `allow_country_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `customer_tag` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `match_logic` tinyint NOT NULL DEFAULT 1 COMMENT '1AND 2OR',
    `status` tinyint(1) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_skill` (`app_key`, `skill_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '队列分流规则' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_agent_skill_rel` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `weight` int NOT NULL DEFAULT 10,
    `enabled` tinyint NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_agent_skill` (`app_key`, `agent_id`, `skill_code`) USING BTREE,
    INDEX `idx_app_skill_agent` (`app_key`, `skill_code`, `enabled`, `del_flag`) USING BTREE,
    INDEX `idx_app_agent_skill` (`app_key`, `agent_id`, `enabled`, `del_flag`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席-技能关联' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_schedule_template` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `template_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `shift_type` tinyint NOT NULL DEFAULT 1 COMMENT '1早班 2晚班 3夜班 4自定义',
    `work_start_time` time NOT NULL,
    `work_end_time` time NOT NULL,
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `status` tinyint(1) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_template` (`app_key`, `template_code`) USING BTREE,
    INDEX `idx_app_status` (`app_key`, `status`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班次模板' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_agent_schedule` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `effective_start_date` date NOT NULL,
    `effective_end_date` date NOT NULL,
    `week_work_rule` varchar(7) NOT NULL DEFAULT '1111100',
    `holiday_dates` json NULL,
    `work_override_dates` json NULL,
    `status` tinyint(1) NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_agent_effective` (`app_key`, `agent_id`, `status`, `effective_start_date`, `effective_end_date`) USING BTREE,
    INDEX `idx_app_template` (`app_key`, `template_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席排班计划' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 4. 策略 / 租户配置 / 外渠
-- =============================================================================

CREATE TABLE IF NOT EXISTS `cs_idle_policy` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `scope_type` tinyint NOT NULL COMMENT '0平台 1merchant 2entry 3skill 4agent',
    `scope_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'merchantId/entryCode/skillCode/agentId/*',
    `enabled` tinyint NOT NULL DEFAULT 1,
    `visitor_idle_warn_sec` int NOT NULL DEFAULT 120,
    `visitor_idle_host_sec` int NOT NULL DEFAULT 180,
    `visitor_idle_close_sec` int NOT NULL DEFAULT 300,
    `visitor_idle_close_enabled` tinyint NOT NULL DEFAULT 1,
    `agent_reply_warn_sec` int NOT NULL DEFAULT 60,
    `agent_reply_host_sec` int NOT NULL DEFAULT 120,
    `agent_reply_escalate_sec` int NOT NULL DEFAULT 300,
    `max_hosted_ratio` decimal(4, 2) NOT NULL DEFAULT 0.50,
    `max_hosted_absolute` int NULL DEFAULT NULL,
    `hosted_max_idle_sec` int NOT NULL DEFAULT 86400,
    `resume_on_visitor_message` tinyint NOT NULL DEFAULT 1,
    `action_json` json NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_idle_policy_scope` (`app_key`, `scope_type`, `scope_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客服空闲/托管策略' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_tenant_config` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户',
    `config_group` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'general|routing',
    `config_json` json NOT NULL COMMENT '配置 JSON',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_group` (`app_key`, `config_group`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '租户级通用/路由配置' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_external_channel_config` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT '*' COMMENT '商户ID，* 为租户默认',
    `channel` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'whatsapp|telegram|line',
    `enabled` tinyint NOT NULL DEFAULT 0,
    `config_json` json NOT NULL,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_merchant_channel` (`app_key`, `merchant_id`, `channel`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '外渠下行凭据' ROW_FORMAT = Dynamic;

-- =============================================================================
-- 5. 坐席工作台 / 知识库
-- =============================================================================

CREATE TABLE IF NOT EXISTS `cs_agent_profile` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `display_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `signature` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `agent_locale` varchar(16) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'zh',
    `notify_sound` tinyint NOT NULL DEFAULT 1,
    `notify_desktop` tinyint NOT NULL DEFAULT 1,
    `notify_new_session` tinyint NOT NULL DEFAULT 1,
    `auto_translate_in` tinyint NOT NULL DEFAULT 1,
    `auto_translate_out` tinyint NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_agent` (`app_key`, `agent_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席个人偏好' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_agent_quick_reply` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL DEFAULT 'other',
    `shortcut` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL,
    `title` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `sort` int NOT NULL DEFAULT 0,
    `enabled` tinyint NOT NULL DEFAULT 1,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_agent_sort` (`app_key`, `agent_id`, `sort`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席常用语' ROW_FORMAT = Dynamic;

CREATE TABLE IF NOT EXISTS `cs_knowledge_article` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `title` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `content` text CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL,
    `category` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT 'faq',
    `tags` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '逗号分隔',
    `enabled` tinyint NOT NULL DEFAULT 1,
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_title` (`app_key`, `title`(64)) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '知识库文章' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;

-- =============================================================================
-- 可选：开发种子数据（按需取消注释）
-- =============================================================================
/*
INSERT IGNORE INTO cs_skill (id, app_key, skill_code, skill_name, status, max_queue_num, queue_timeout_second, overflow_strategy, priority, del_flag)
VALUES (1001, 'default', 'GENERAL', '通用队列', 1, 50, 300, 1, 10, 0);

INSERT IGNORE INTO cs_agent (id, app_key, agent_id, agent_name, job_no, agent_type, max_concurrent, enabled, del_flag)
VALUES (2001, 'default', '1', '坐席张三', 'A001', 1, 5, 1, 0);

INSERT IGNORE INTO cs_agent_skill_rel (id, app_key, agent_id, skill_code, weight, enabled, del_flag)
VALUES
 (3001, 'default', '1', 'GENERAL', 10, 1, 0);

INSERT IGNORE INTO cs_merchant (id, app_key, merchant_id, merchant_name, enabled, del_flag)
VALUES (4001, 'default', 'demo_shop', '演示商家', 1, 0);

INSERT IGNORE INTO cs_merchant_entry (id, app_key, merchant_id, entry_code, entry_name, service_identity, is_default, enabled, del_flag)
VALUES
 (5001, 'default', 'demo_shop', 'main', '主品牌客服', 'cs_demo_main', 1, 1, 0),
 (5002, 'default', 'demo_shop', 'luxury', '高端线客服', 'cs_demo_luxury', 0, 1, 0);
*/
