-- message.ouyunc_im_app definition

CREATE TABLE `ouyunc_im_app` (
                                 `id` bigint NOT NULL COMMENT '主键id',
                                 `app_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端（外部平台）key  唯一',
                                 `app_secret` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端 （外部平台）secret',
                                 `app_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '客户端 （外部平台）name',
                                 `user_id` bigint NOT NULL COMMENT '用户id，一般是企业的账户',
                                 `max_connections` bigint NOT NULL DEFAULT '0' COMMENT 'IM 最大连接数 大于等于-1： -1 - 无限制，',
                                 `status` tinyint NOT NULL DEFAULT '1' COMMENT '1-有效，2-禁用/锁定/无效',
                                 `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                 `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                 `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
                                 PRIMARY KEY (`id`) USING BTREE,
                                 KEY `idx_app_key` (`app_key`) USING BTREE,
                                 KEY `idx_user_id_app_key` (`user_id`,`app_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='im 应用配置';




-- message.ouyunc_im_blacklist definition

CREATE TABLE `ouyunc_im_blacklist` (
                                       `id` bigint(20) NOT NULL COMMENT '主键id',
                                       `identity` bigint(20) NOT NULL COMMENT '群或客户端唯一标识',
                                       `user_id` bigint(20) NOT NULL COMMENT '客户端id（被加入identity 黑名单）',
                                       `identity_type` tinyint(1) NOT NULL COMMENT '唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识',
                                       `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                       `join_time` bigint(20) DEFAULT NULL COMMENT '加入黑名单时间戳，毫秒',
                                       PRIMARY KEY (`id`) USING BTREE,
                                       UNIQUE KEY `identity_userId` (`identity`,`user_id`) USING BTREE COMMENT '关系唯一索引',
                                       KEY `identity_userId_type` (`identity`,`user_id`,`identity_type`) USING BTREE COMMENT '联合索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 ROW_FORMAT=DYNAMIC COMMENT='黑名单表';


-- message.ouyunc_im_file definition

CREATE TABLE `ouyunc_im_file` (
                                  `id` bigint NOT NULL COMMENT '主键id',
                                  `file_origin_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件原始名称',
                                  `file_name` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件名称，包含后缀名',
                                  `file_url` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件网络访问完整路径：http(s)://xxxxx',
                                  `file_path` varchar(500) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件半路径，一般为存放目录+文件名',
                                  `file_type` tinyint(1) NOT NULL COMMENT '文件类型，标识是哪种业务类型的文件：1-图片文件，2-文档文件，3-声音文件，4-视频文件，5-压缩文件，6-其他',
                                  `file_md5` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件的md5',
                                  `file_suffix` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '文件后缀名',
                                  `relation_type` tinyint NOT NULL DEFAULT '1' COMMENT '文件关联类型：用来标识该业务id的来源',
                                  `relation_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '文件关联id',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：0-未删除，1-已删除',
                                  PRIMARY KEY (`id`) USING BTREE,
                                  KEY `idx_relation_id` (`relation_id`) USING BTREE,
                                  KEY `idx_relation_type_id` (`relation_type`,`relation_id`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='文件表';



-- message.ouyunc_im_friend definition

CREATE TABLE `ouyunc_im_friend` (
                                    `id` bigint NOT NULL COMMENT '主键id',
                                    `user_id` bigint DEFAULT NULL COMMENT '用户id',
                                    `friend_user_id` bigint DEFAULT NULL COMMENT '好友用户id',
                                    `friend_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '好友昵称',
                                    `shield` tinyint(1) DEFAULT NULL DEFAULT '0' COMMENT '是否屏蔽该好友，0-未屏蔽，1-屏蔽',
                                    `session_message_offset` bigint NOT NULL DEFAULT '0' COMMENT '会话消息偏移量，会话消息的接收时间；假如本次读取到会话A点，则下次从A点之后开始读取',
                                    `join_time` bigint NOT NULL COMMENT '成为好友的时间戳',
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                    PRIMARY KEY (`id`) USING BTREE,
                                    UNIQUE KEY `user_friend_user_id` (`user_id`,`friend_user_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='好友表';

-- message.ouyunc_im_group definition

CREATE TABLE `ouyunc_im_group` (
                                   `id` bigint NOT NULL COMMENT '主键id',
                                   `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '群组名称',
                                   `group_avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '群组头像',
                                   `group_description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '群组描述',
                                   `group_announcement` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '群组公告',
                                   `group_join_policy` tinyint(1) NOT NULL DEFAULT '0' COMMENT '群加入策略：0-加群需要验证，1-加群自动同意',
                                   `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '群状态，1-正常，2-异常（被平台封禁）',
                                   `silence` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否全体禁言（群主和管理员除外），0-不禁言，1-禁言',
                                   `app_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用appKey',
                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                   `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除：1-已删除，0-未删除',
                                   PRIMARY KEY (`id`) USING BTREE,
                                   KEY `idx_app_key_group_name` (`app_key`,`group_name`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='群信息表';


-- message.ouyunc_im_group_user definition

CREATE TABLE `ouyunc_im_group_user` (
                                        `id` bigint NOT NULL COMMENT '主键id',
                                        `group_id` bigint NOT NULL COMMENT '群组id',
                                        `group_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '群组别名（该用户对这个群起的别名）',
                                        `user_id` bigint DEFAULT NULL COMMENT '用户id',
                                        `leader` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否是群主，0-否，1-是',
                                        `manager` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否是群管理员，0-否，1-是',
                                        `user_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户昵称（用户在群里的昵称）',
                                        `shield` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽',
                                        `silence` tinyint(1) NOT NULL DEFAULT '0' COMMENT '用户在群中的状态，0-未被禁言，1-被禁言',
                                        `join_time` bigint NOT NULL COMMENT '加入群的时间戳',
                                        `session_message_offset` bigint NOT NULL DEFAULT '0' COMMENT '会话消息的偏移量；群成员对群会话的消息读取偏移量，假如这次读取到A点，则下次从A点开始往后拉取数据',
                                        `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                        PRIMARY KEY (`id`) USING BTREE,
                                        UNIQUE KEY `group_user_id` (`user_id`,`group_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='群成员表';



-- message.ouyunc_im_message definition

CREATE TABLE `ouyunc_im_message` (
                                     `id` bigint NOT NULL COMMENT '主键消息id (packetId)',
                                     `protocol` tinyint(1) NOT NULL COMMENT '消息协议类型',
                                     `protocol_version` tinyint(1) NOT NULL COMMENT '消息协议版本号',
                                     `device_type` tinyint(1) NOT NULL COMMENT '设备类型',
                                     `network_type` tinyint(1) NOT NULL COMMENT '网络类型',
                                     `encrypt_type` tinyint(1) NOT NULL COMMENT '消息加密算法',
                                     `serialize_algorithm` tinyint(1) NOT NULL COMMENT '消息内容序列化算法',
                                     `message_type` tinyint(1) NOT NULL COMMENT '消息类型：心跳，群聊，私聊...',
                                     `retain` tinyint(1) NOT NULL DEFAULT '0' COMMENT '保留位，1个字节',
                                     `client_ip` varchar(40) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端ip',
                                     `from` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '消息发送者',
                                     `to` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息接收者/群组',
                                     `content_type` int NOT NULL COMMENT '消息内容类型: 文本，图片，音频...',
                                     `content` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息内容',
                                     `app_key` varchar(64) COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用唯一标识',
                                     `extra` varchar(2000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '消息内容扩展字段',
                                     `at` json DEFAULT NULL COMMENT '@功能，这里存放客户端登录绑定的id',
                                     `qos` tinyint(1) NOT NULL COMMENT '消息可靠性标识 qos = 0/1/2\r\n     * QoS 0：至多一次，at most once；发送方发送一条消息，接收方最多能接收到一次。即发送方完成消息发送之后不关心消息发送是否成功。\r\n     * QoS 1：至少一次，at least once；发送方发送一条消息，接收方至少能接收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接受方接收到消息为止。这种模式下可能会导致接收方收到重复的消息。\r\n     * QoS 2：确保一次：exactly once；发送方发送一条消息，接收方一定且只能收到一次。即发送方完成消息发送之后，若发送失败，则继续重发直到接收方接收到消息为止，在这一过程中同时保证接收方不会因为消息重传而收到重复的消息。',
                                     `client_send_time` bigint NOT NULL COMMENT '消息发送时间戳',
                                     `server_arrival_time` bigint NOT NULL COMMENT '消息首次到达服务端时间戳',
                                     `read` bit(1) NOT NULL DEFAULT b'0' COMMENT '已读（只针对单聊有效，该字段对群聊业务无效）：0-未读，1-已读',
                                     `withdrawn` bit(1) NOT NULL DEFAULT b'0' COMMENT '已撤回：0-未撤回，1-已撤回',
                                     `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                     `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '默认删除，1-已删除，0-未删除',
                                     PRIMARY KEY (`id`) USING BTREE,
                                     KEY `idx_from_to` (`from`,`to`) USING BTREE COMMENT 'from_to组合索引',
                                     KEY `idx_message_type` (`message_type`) USING BTREE COMMENT '消息类型索引',
                                     KEY `idx_client_send_time` (`client_send_time`) USING BTREE COMMENT '客户端发送时间索引',
                                     KEY `idx_server_arrival_time` (`server_arrival_time`) USING BTREE COMMENT '服务端到达时间索引',
                                     KEY `idx_to` (`to`) USING BTREE COMMENT 'to索引',
                                     KEY `idx_message_content_type` (`content_type`) USING BTREE COMMENT '消息内容类型索引',
                                     KEY `idx_app_key` (`app_key`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='im 消息业务全量存储表';


-- message.ouyunc_im_read_receipt definition

CREATE TABLE `ouyunc_im_read_receipt` (
                                          `id` bigint NOT NULL COMMENT '主键',
                                          `msg_id` bigint NOT NULL COMMENT '消息id,(packetId)',
                                          `user_id` bigint NOT NULL COMMENT '已读消息的用户id',
                                          `read_time` bigint NOT NULL COMMENT '已读时间戳',
                                          `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                          PRIMARY KEY (`id`) USING BTREE,
                                          UNIQUE KEY `ouyunc_im_read_receipt_unique` (`msg_id`,`user_id`),
                                          KEY `idx_msg_id` (`msg_id`) USING BTREE COMMENT '消息id'
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='消息已读回执';



-- message.ouyunc_im_user definition

CREATE TABLE `ouyunc_im_user` (
                                  `id` bigint NOT NULL COMMENT '主键id',
                                  `open_id` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '开放id',
                                  `username` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名称（对应于身份证）',
                                  `password` varchar(150) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户名密码',
                                  `nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户别名',
                                  `avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '用户头像url',
                                  `motto` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '座右铭/格言',
                                  `age` tinyint(1) DEFAULT NULL COMMENT '年龄',
                                  `sex` tinyint(1) DEFAULT NULL COMMENT '性别：0-女，1-男，2-其他',
                                  `email` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '邮箱',
                                  `phone_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '手机号（国内）',
                                  `id_card_num` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '身份证号码',
                                  `group_invite_policy` tinyint(1) NOT NULL DEFAULT '0' COMMENT '群邀请的应答策略：0-需要验证，1-自动通过',
                                  `friend_join_policy` tinyint(1) NOT NULL DEFAULT '0' COMMENT '好友添加的应答策略：0-需要验证，1-自动通过',
                                  `status` tinyint(1) NOT NULL DEFAULT '1' COMMENT '用户状态：1-正常，2-异常（被平台封禁）',
                                  `app_key` varchar(64) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '应用appKey',
                                  `robot` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否是机器人：0-不是，1-是',
                                  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
                                  `deleted` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否删除，1-已删除，0-未删除',
                                  PRIMARY KEY (`id`) USING BTREE,
                                  UNIQUE KEY `idx_open_id` (`open_id`) USING BTREE,
                                  KEY `idx_app_key` (`app_key`) USING BTREE,
                                  KEY `idx_email` (`email`) USING BTREE,
                                  KEY `idx_phone_num` (`phone_num`) USING BTREE,
                                  KEY `idx_id_card_num` (`id_card_num`) USING BTREE,
                                  KEY `idx_app_key_username` (`app_key`,`username`) USING BTREE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci ROW_FORMAT=DYNAMIC COMMENT='用户表';