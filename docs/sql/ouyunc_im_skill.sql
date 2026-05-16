-- 技能/队列配置（skill_code 与路由、Redis 队列 Key 对齐）
CREATE TABLE IF NOT EXISTS `ouyunc_im_skill` (
  `id` bigint(20) NOT NULL COMMENT '主键ID',
  `app_key` varchar(64) NOT NULL COMMENT '应用Key',
  `skill_code` varchar(64) NOT NULL COMMENT '技能编码=队列编码（用于Redis Key）',
  `skill_name` varchar(64) NOT NULL COMMENT '技能/队列名称（前端显示）',
  `status` tinyint(1) NOT NULL DEFAULT 1 COMMENT '状态：0禁用 1启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_app_skill` (`app_key`,`skill_code`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能/队列配置表';
