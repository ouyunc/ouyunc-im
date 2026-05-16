-- 坐席基础信息（多租户）；与路由层 ouyunc_cs_agent 可并存，由业务约定 agent_id 与 CS 主键映射关系
CREATE TABLE IF NOT EXISTS `ouyunc_im_agent` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `app_key` varchar(64) NOT NULL COMMENT '应用Key（多租户）',
  `agent_id` varchar(64) NOT NULL COMMENT '坐席唯一标识',
  `agent_name` varchar(64) NOT NULL COMMENT '坐席名称',
  `job_no` varchar(32) DEFAULT NULL COMMENT '工号',
  `enabled` tinyint(1) NOT NULL DEFAULT 1 COMMENT '是否启用：0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '删除标识：0未删除 1已删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_agent` (`app_key`,`agent_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席基础信息表';
