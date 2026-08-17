/*
 客服（CS）模块表结构
 Target Schema: ouyunc_cs（用户在 ouyunc_system，IM 在 ouyunc_message）
 MySQL 8.0+ / utf8mb4_0900_ai_ci
 完整脚本（含扩展表）见 cs_schema_full.sql
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 客服客户主数据（IM identity；进线时 resolveOrCreate）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_customer` (
    `id` bigint(20) NOT NULL COMMENT '主键',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '租户 appKey',
    `customer_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户 IM identity，租户内唯一',
    `customer_name` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户昵称',
    `customer_type` tinyint NOT NULL DEFAULT 2 COMMENT '1-注册用户 2-游客（服务端生成 identity）',
    `member_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '业务会员 ID',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '首次进线渠道',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_customer` (`app_key`, `customer_id`) USING BTREE,
    INDEX `idx_app_member` (`app_key`, `member_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '客服客户主数据' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 商家主数据（平台 appKey 下多商家）
-- ----------------------------
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

-- ----------------------------
-- 商家 IM 入口（1 商家 n 入口；每入口对应唯一 service_identity）
-- ----------------------------
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

-- ----------------------------
-- 咨询单表（客服多次进线拆分维度）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_consultation_ticket` (
    `id` bigint(20) NOT NULL COMMENT '主键id',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用 appKey',
    `session_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'IM 会话 id，IdentityUtil.sessionId(用户id, 客服id)',
    `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户用户 id（IM identity，通讯层唯一标识）',
    `user_name` varchar(128) NULL DEFAULT NULL COMMENT '客户昵称',
    `merchant_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '商家 ID',
    `entry_code` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT 'IM 入口编码',
    `user_mobile` varchar(32) NULL DEFAULT NULL COMMENT '客户手机号',
    `user_email` varchar(128) NULL DEFAULT NULL COMMENT '客户邮箱',
    `country_code` char(2) NULL DEFAULT NULL COMMENT 'ISO3166两位国家二字码：CN/US/SG/MY/TH',
    `phone_country_prefix` varchar(8) NULL DEFAULT NULL COMMENT '国际电话区号：+86、+65、+1、+60',
    `service_identity` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客服侧虚拟客服id',
    `status` tinyint NOT NULL DEFAULT 1 COMMENT '状态：1-进行中，2-已关闭，3-已转接',
    `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前接待坐席id',
    `assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '当前接待客服昵称/工号',
    `origin_assignee_id` varchar(64) NULL DEFAULT NULL COMMENT '首次分配坐席ID',
    `transfer_count` tinyint NOT NULL DEFAULT 0 COMMENT '转接次数',
    `is_robot` tinyint NULL DEFAULT 0 COMMENT '当前是否机器人接待：0-人工 1-机器人',
    `is_robot_transfer` tinyint NOT NULL DEFAULT 0 COMMENT '是否机器人转人工：0否1是',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '进线渠道：whatsapp/telegram/line/im/400_call',
    `terminal_type` varchar(16) NULL DEFAULT NULL COMMENT '自有终端类型，仅 channel=im 时有值：APP/MINIAPP/H5/WEB/PC',
    `client_ip` varchar(64) NULL DEFAULT NULL COMMENT '客户真实IP',
    `device_info` varchar(256) NULL DEFAULT NULL COMMENT '设备信息：系统/机型/浏览器',
    `referrer_url` varchar(512) NULL DEFAULT NULL COMMENT '访客来源页面/推广链接',
    `queue_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '来源队列ID（如 app123_pre_sale）',
    `queue_in_time` datetime NULL DEFAULT NULL COMMENT '进入队列时间',
    `queue_wait_second` int NULL DEFAULT 0 COMMENT '排队等待时长(秒)',
    `queue_status` tinyint NULL DEFAULT 0 COMMENT '排队状态：0-未排队 1-已分配 2-超时',
    `start_time` datetime NULL DEFAULT NULL COMMENT '本次咨询开始时间（进线时间）',
    `end_time` datetime NULL DEFAULT NULL COMMENT '本次咨询结束时间（关单时间）',
    `consult_duration_second` int NULL DEFAULT 0 COMMENT '本次咨询总时长(秒)',
    `close_type` tinyint NULL DEFAULT NULL COMMENT '关单类型：1客户主动关闭 2客服关闭 3超时自动关闭 4转接关闭',
    `biz_order_no` varchar(128) NULL DEFAULT NULL COMMENT '关联业务订单号',
    `member_id` varchar(64) NULL DEFAULT NULL COMMENT '业务会员ID，登录业务账号才有值，游客为空',
    `biz_tag` varchar(256) NULL DEFAULT NULL COMMENT '业务标签，逗号分隔：售前/售后/退款/物流',
    `satisfaction_score` tinyint NULL DEFAULT NULL COMMENT '满意度：1差评 2一般 3好评',
    `satisfaction_comment` varchar(500) NULL DEFAULT NULL COMMENT '客户评价内容',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注（关单/转接原因）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    `active_guard` tinyint GENERATED ALWAYS AS (IF(`del_flag` = 0 AND `status` = 1, 1, NULL)) STORED COMMENT '进行中占位：1=占用唯一槽',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_key` (`app_key`) USING BTREE,
    INDEX `idx_app_session` (`app_key`, `session_id`) USING BTREE COMMENT '按会话查咨询单',
    INDEX `idx_app_user` (`app_key`, `user_id`) USING BTREE COMMENT '客户历史咨询',
    UNIQUE INDEX `uk_app_user_service_active` (`app_key`, `user_id`, `service_identity`, `active_guard`) USING BTREE COMMENT '同一客户在同一 IM 入口仅一条进行中工单',
    INDEX `idx_app_merchant_user` (`app_key`, `merchant_id`, `user_id`) USING BTREE COMMENT '按商家查客户咨询',
    INDEX `idx_app_assignee_status` (`app_key`, `assignee_id`, `status`) USING BTREE COMMENT '坐席待处理工单',
    INDEX `idx_app_start_time` (`app_key`, `start_time`) USING BTREE COMMENT '进线时间范围统计',
    INDEX `idx_app_channel` (`app_key`, `channel`) USING BTREE COMMENT '渠道数据统计',
    INDEX `idx_app_biz_order` (`app_key`, `biz_order_no`) USING BTREE COMMENT '订单关联工单查询',
    INDEX `idx_app_country` (`app_key`, `country_code`) USING BTREE COMMENT '按国家统计进线数据'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '咨询单表（客服多次进线拆分维度）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 咨询记录表（咨询单流转明细：每次转接/接入/关单生成一条，用于时间线与处理人维度查询）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_consultation_ticket_log` (
    `id` bigint(20) NOT NULL COMMENT '主键id',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用 appKey',
    `ticket_id` bigint(20) NOT NULL COMMENT '咨询单 id，关联 cs_consultation_ticket.id',
    `session_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT 'IM会话ID，冗余存储，减少联查',
    `user_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '客户IM身份ID，冗余',
    `channel` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '进线渠道，冗余：whatsapp/telegram/line/im/400_call',
    `record_type` tinyint NOT NULL COMMENT '记录类型：1-首次接入，2-转接转入，3-转出（结束本次接待），4-关单',
    `operate_type` tinyint NULL DEFAULT NULL COMMENT '操作细分：1正常接待 2人工主动转接 3超时关闭 4客户离线关闭 5系统强制关单',
    `assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '本段处理人 id（坐席 identity）',
    `assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '本段处理人昵称/工号',
    `is_robot` tinyint NULL DEFAULT 0 COMMENT '本段接待是否机器人：0-人工 1-机器人',
    `bot_flow_id` varchar(64) NULL DEFAULT NULL COMMENT '机器人接待流程ID，仅机器人接待时有值',
    `transfer_mode` tinyint NULL DEFAULT NULL COMMENT '转接方式：1人工手动转接 2系统自动分配转接',
    `from_assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转接转入时：上一段转出坐席id(record_type=2)',
    `from_assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转接转入时：上一段转出坐席名称',
    `to_assignee_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转接转出时：下一段接收坐席id(record_type=3)',
    `to_assignee_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '转接转出时：下一段接收坐席名称',
    `from_queue_id` varchar(64) NULL DEFAULT NULL COMMENT '转出队列ID，跨队列转接场景使用',
    `to_queue_id` varchar(64) NULL DEFAULT NULL COMMENT '转入队列ID，跨队列转接场景使用',
    `queue_wait_second` int NULL DEFAULT 0 COMMENT '本段分配前排队等待时长(秒)',
    `handle_duration_second` int NULL DEFAULT 0 COMMENT '本段接待总时长(秒)',
    `satisfaction` tinyint NULL DEFAULT NULL COMMENT '本段满意度：1-非常不满意 2-不满意 3-一般 4-满意 5-非常满意',
    `satisfaction_comment` varchar(500) NULL DEFAULT NULL COMMENT '客户评价文字内容',
    `start_time` bigint(20) NOT NULL COMMENT '本段开始时间，毫秒时间戳',
    `end_time` bigint(20) NULL DEFAULT NULL COMMENT '本段结束时间（转出或关单时填），毫秒时间戳',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '备注（转接原因、关单原因等）',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '记录创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '记录更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_ticket` (`app_key`, `ticket_id`) USING BTREE COMMENT '按咨询单查完整流转时间线',
    INDEX `idx_app_assignee_time` (`app_key`, `assignee_id`, `start_time`) USING BTREE COMMENT '坐席接待明细+时间统计',
    INDEX `idx_app_assignee_channel` (`app_key`, `assignee_id`, `channel`) USING BTREE COMMENT '坐席分渠道业绩统计',
    INDEX `idx_app_user` (`app_key`, `user_id`) USING BTREE COMMENT '单个客户全部流转记录',
    INDEX `idx_app_start_time` (`app_key`, `start_time`) USING BTREE COMMENT '全量时间范围报表统计'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '咨询记录表（咨询单流转，每次转接/接入/关单一条）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 坐席基础信息（多租户）；与路由层 ouyunc_cs_agent 可并存，由业务约定 agent_id 与 CS 主键映射关系
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_agent` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key（多租户隔离）',
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席标识，等于 sys_user.id（字符串）',
    `agent_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席昵称/姓名',
    `job_no` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '员工工号',
    `avatar` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '坐席头像链接',
    `mobile` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '坐席手机号',
    `email` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '坐席邮箱',
    `agent_type` tinyint NOT NULL DEFAULT 1 COMMENT '坐席类型：1人工坐席 2机器人坐席 3虚拟客服',
    `max_concurrent` int NOT NULL DEFAULT 5 COMMENT '最大并发接待会话数，路由限流使用',
    `languages` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '支持语种，多语种逗号分隔：CN,EN,SG,TH',
    `receive_channel` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '可接待渠道，逗号分隔：im,whatsapp,telegram,line,400_call',
    `is_auto_distribute` tinyint NOT NULL DEFAULT 1 COMMENT '是否允许系统自动分配会话：0否 1是',
    `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '账号启用状态：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_agent` (`app_key`, `agent_id`) USING BTREE,
    INDEX `idx_app_jobno` (`app_key`, `job_no`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席静态配置信息表（多租户）' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 技能/队列基础配置（skill_code 与路由、Redis 队列 Key 对齐）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_skill` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key（多租户隔离）',
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '技能编码=队列编码（对齐Redis路由Key）',
    `skill_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '技能/队列名称（前端展示）',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '队列备注：售前/售后/物流/海外英文专线',
    `max_queue_num` int NOT NULL DEFAULT 20 COMMENT '队列最大排队人数，超出触发溢出策略',
    `queue_timeout_second` int NOT NULL DEFAULT 300 COMMENT '排队超时秒数，超时自动溢出',
    `overflow_strategy` tinyint NOT NULL DEFAULT 1 COMMENT '溢出策略：1转兜底队列 2自动关闭会话 3保留等待',
    `overflow_skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '溢出兜底队列编码',
    `priority` int NOT NULL DEFAULT 10 COMMENT '队列优先级，数值越小优先级越高',
    `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_skill` (`app_key`, `skill_code`) USING BTREE COMMENT '租户内队列编码唯一',
    INDEX `idx_app_name` (`app_key`, `skill_name`) USING BTREE COMMENT '按队列名称模糊查询'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '技能/队列基础信息表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 队列分流匹配规则表，一个 skill_code 可多条规则
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_skill_rule` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key',
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '关联队列编码，关联 cs_skill.skill_code',
    `rule_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '规则名称',
    `sort` int NOT NULL DEFAULT 1 COMMENT '规则匹配顺序，数字越小越先匹配',
    `allow_channel` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '允许进线渠道，逗号分隔：im,whatsapp,telegram,line,400_call；空不限制',
    `allow_country_code` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '允许国家二字码，逗号分隔：CN,US,SG,TH；空不限制',
    `customer_tag` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '客户业务标签匹配，逗号分隔；空不限制',
    `match_logic` tinyint NOT NULL DEFAULT 1 COMMENT '多条件逻辑：1全部满足AND 2任一满足OR',
    `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '规则状态：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_skill` (`app_key`, `skill_code`) USING BTREE COMMENT '根据队列查所有分流规则'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '队列分流匹配规则表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 坐席与技能/队列的权限关联中间表（多对多）
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_agent_skill_rel` (
    `id` bigint(20) NOT NULL COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key（多租户隔离）',
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席唯一标识',
    `skill_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '技能/队列编码，关联 cs_skill.skill_code',
    `weight` int NOT NULL DEFAULT 10 COMMENT '分配权重，数值越大分配优先级越高',
    `enabled` tinyint NOT NULL DEFAULT 1 COMMENT '该队列权限是否生效：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '绑定创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '绑定更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_agent_skill` (`app_key`, `agent_id`, `skill_code`) USING BTREE,
    INDEX `idx_app_skill_agent` (`app_key`, `skill_code`, `enabled`, `del_flag`) USING BTREE,
    INDEX `idx_app_agent_skill` (`app_key`, `agent_id`, `enabled`, `del_flag`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席-技能/队列权限关联中间表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 班次模板配置表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_schedule_template` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key（多租户隔离）',
    `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班次编码，租户内唯一',
    `template_name` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '班次名称：早班/晚班/夜班/自定义班次',
    `shift_type` tinyint NOT NULL DEFAULT 1 COMMENT '班次类型：1早班 2晚班 3夜班 4自定义',
    `work_start_time` time NOT NULL COMMENT '上班开始时间，如 09:00:00',
    `work_end_time` time NOT NULL COMMENT '上班结束时间，如 18:00:00',
    `remark` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci DEFAULT NULL COMMENT '班次备注说明',
    `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '启用状态：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    UNIQUE KEY `uk_app_template` (`app_key`, `template_code`) USING BTREE COMMENT '租户内班次编码唯一',
    INDEX `idx_app_status` (`app_key`, `status`) USING BTREE COMMENT '查询租户内所有启用的班次模板'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '班次模板配置表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- 坐席排班计划表
-- ----------------------------
CREATE TABLE IF NOT EXISTS `cs_agent_schedule` (
    `id` bigint(20) NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '应用Key（多租户隔离）',
    `agent_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '坐席ID，关联 cs_agent.agent_id',
    `template_code` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '绑定的班次模板编码，关联 cs_schedule_template.template_code',
    `effective_start_date` date NOT NULL COMMENT '排班生效开始日期',
    `effective_end_date` date NOT NULL COMMENT '排班生效结束日期，长期有效可设为2099-12-31',
    `week_work_rule` varchar(7) NOT NULL DEFAULT '1111100' COMMENT '周排班规则，7位字符串，默认周一到周五上班，周六日休息',
    `holiday_dates` json NULL COMMENT '节假日休息日期数组，如 ["2026-05-01","2026-10-01"]',
    `work_override_dates` json NULL COMMENT '调休上班日期数组，如 ["2026-04-27"]',
    `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '排班启用状态：0禁用 1启用',
    `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `del_flag` bigint NOT NULL DEFAULT 0 COMMENT '软删除：0-未删除；非0-已删除（毫秒时间戳）',
    PRIMARY KEY (`id`) USING BTREE,
    INDEX `idx_app_agent_effective` (`app_key`, `agent_id`, `status`, `effective_start_date`, `effective_end_date`) USING BTREE,
    INDEX `idx_app_template` (`app_key`, `template_code`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = '坐席排班计划表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
