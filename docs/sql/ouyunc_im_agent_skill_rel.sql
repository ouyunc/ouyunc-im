-- 坐席与技能/队列的权限关系（多租户）
CREATE TABLE IF NOT EXISTS `ouyunc_im_agent_skill_rel` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `app_key` varchar(64) NOT NULL COMMENT '应用Key',
  `agent_id` varchar(64) NOT NULL COMMENT '坐席ID',
  `skill_code` varchar(64) NOT NULL COMMENT '技能/队列编码',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_agent_skill` (`app_key`,`agent_id`,`skill_code`) USING BTREE,
  KEY `idx_query_agent` (`app_key`,`skill_code`) USING BTREE COMMENT '根据队列查询可接待坐席'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='坐席-技能/队列权限关系表';
