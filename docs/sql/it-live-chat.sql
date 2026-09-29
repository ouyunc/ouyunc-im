-- 自动化私聊联调数据。在 dev 库 ouyunc_message 执行。
-- 应用 ouyunc_it / ItSecret#2026，用户 910001 与 910002 互为好友。
-- 可重复执行。

INSERT INTO ouyunc_im_app (id, app_key, app_secret, app_name, user_id, max_connections, status, create_time, update_time, del_flag)
VALUES (910000, 'ouyunc_it', 'ItSecret#2026', 'IM自动化', 910001, -1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE app_secret = VALUES(app_secret), max_connections = -1, status = 1, del_flag = 0;

INSERT INTO ouyunc_im_user (id, open_id, code, nick_name, status, app_key, type, group_invite_policy, friend_join_policy, create_time, update_time, del_flag)
VALUES
  (910001, 'it-open-910001', 'it910001', 'it-a', 1, 'ouyunc_it', 1, 1, 1, NOW(), NOW(), 0),
  (910002, 'it-open-910002', 'it910002', 'it-b', 1, 'ouyunc_it', 1, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE app_key = VALUES(app_key), status = 1, del_flag = 0;

INSERT INTO ouyunc_im_friend (id, user_id, friend_user_id, friend_user_code, friend_nick_name, shield, way, channel, join_time, create_time, update_time)
VALUES
  (910011, 910001, 910002, 'it910002', 'it-b', 0, 1, 1, UNIX_TIMESTAMP() * 1000, NOW(), NOW()),
  (910012, 910002, 910001, 'it910001', 'it-a', 0, 1, 1, UNIX_TIMESTAMP() * 1000, NOW(), NOW())
ON DUPLICATE KEY UPDATE shield = 0, friend_user_id = VALUES(friend_user_id);

INSERT INTO ouyunc_im_user (id, open_id, code, nick_name, status, app_key, type, group_invite_policy, friend_join_policy, create_time, update_time, del_flag)
VALUES (910003, 'it-open-910003', 'it910003', 'it-stranger', 1, 'ouyunc_it', 1, 1, 1, NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE app_key = VALUES(app_key), status = 1, del_flag = 0;

INSERT INTO ouyunc_im_group (id, group_code, group_name, group_join_policy, status, silence, app_key, create_time, update_time, del_flag)
VALUES (910100, 'it910100', 'it-group', 1, 1, 0, 'ouyunc_it', NOW(), NOW(), 0)
ON DUPLICATE KEY UPDATE status = 1, del_flag = 0, app_key = VALUES(app_key);

INSERT INTO ouyunc_im_group_user (id, group_id, group_code, user_id, user_code, post, shield, silence, way, channel, join_time, create_time)
VALUES
  (910101, 910100, 'it910100', 910001, 'it910001', 2, 0, 0, 1, 1, UNIX_TIMESTAMP() * 1000, NOW()),
  (910102, 910100, 'it910100', 910002, 'it910002', 0, 0, 0, 1, 1, UNIX_TIMESTAMP() * 1000, NOW())
ON DUPLICATE KEY UPDATE shield = 0, silence = 0;
