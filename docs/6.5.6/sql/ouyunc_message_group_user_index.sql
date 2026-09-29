-- 群成员冷启动、群渠道批查均以 group_id 为前导条件。
-- 执行前必须在真实生产结构上运行 EXPLAIN ANALYZE，并确认不存在等价索引。
-- 大表执行期间持续观察 metadata lock、磁盘余量、主从延迟和业务 P99；异常时按变更窗口回滚。
ALTER TABLE `ouyunc_im_group_user`
    ADD INDEX `idx_group_user_group_id_user_id` (`group_id`, `user_id`),
    ALGORITHM=INPLACE,
    LOCK=NONE;
