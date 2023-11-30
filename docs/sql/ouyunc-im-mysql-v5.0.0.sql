/*
 Navicat Premium Data Transfer

 Source Server         : 110.42.254.201
 Source Server Type    : MySQL
 Source Server Version : 80033
 Source Host           : 110.42.254.201:3306
 Source Schema         : ouyunc-im-v2

 Target Server Type    : MySQL
 Target Server Version : 80033
 File Encoding         : 65001

 Date: 28/11/2023 12:42:55
*/

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- Table structure for oauth_client_details
-- ----------------------------
DROP TABLE IF EXISTS `oauth_client_details`;
CREATE TABLE `oauth_client_details`  (
  `client_id` varchar(128) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NOT NULL COMMENT '用于唯一标识每一个客户端(client); 在注册时必须填写(也可由服务端自动生成).\r\n对于不同的grant_type,该字段都是必须的. 在实际应用中的另一个名称叫appKey,与client_id是同一个概念.',
  `client_secret` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '用于指定客户端(client)的访问密匙; 在注册时必须填写(也可由服务端自动生成).\r\n对于不同的grant_type,该字段都是必须的. 在实际应用中的另一个名称叫appSecret,与client_secret是同一个概念.',
  `client_name` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端名称（自己添加不属于oauth2）',
  `resource_ids` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端所能访问的资源id集合,多个资源时用逗号(,)分隔,如: \"unity-resource,mobile-resource\".\n\n可以根据上图知道，我们有Resource Server资源服务器。，资源服务器可以有多个，我们可以为每一个Resource Server（一个微服务实例）设置一个resourceid。\n\nAuthorization Server给client第三方客户端授权的时候，可以设置这个client可以访问哪一些Resource Server资源服务，如果没设置，就是对所有的Resource Server都有访问权限。',
  `scope` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '指定客户端申请的权限范围,可选值包括read,write,trust;若有多个权限范围用逗号(,)分隔,如: \"read,write\".\n\n@EnableGlobalMethodSecurity(prePostEnabled = true)启用方法级权限控制\n\n然后在方法上注解标识@PreAuthorize(\"#oauth2.hasScope(\'read\')\")',
  `authorized_grant_types` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '指定客户端支持的grant_type,可选值包括authorization_code,password,refresh_token,implicit,client_credentials, 若支持多个grant_type用逗号(,)分隔,如: \"authorization_code,password\".\n在实际应用中,当注册时,该字段是一般由服务器端指定的,而不是由申请者去选择的,最常用的grant_type组合有: \"authorization_code,refresh_token\"(针对通过浏览器访问的客户端); \"password,refresh_token\"(针对移动设备的客户端).\nimplicit与client_credentials在实际中很少使用，可以根据自己的需要，在OAuth2.0 提供的地方进行扩展自定义的授权',
  `web_server_redirect_uri` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '客户端的重定向URI,可为空, 当grant_type为authorization_code或implicit时, 在Oauth的流程中会使用并检查与注册时填写的redirect_uri是否一致. 下面分别说明:\n当grant_type=authorization_code时, 第一步 从 spring-oauth-server获取 \'code\'时客户端发起请求时必须有redirect_uri参数, 该参数的值必须与 web_server_redirect_uri的值一致. 第二步 用 \'code\' 换取 \'access_token\' 时客户也必须传递相同的redirect_uri.\n在实际应用中, web_server_redirect_uri在注册时是必须填写的, 一般用来处理服务器返回的code, 验证state是否合法与通过code去换取access_token值.\n在spring-oauth-client项目中, 可具体参考AuthorizationCodeController.java中的authorizationCodeCallback方法.\n当grant_type=implicit时通过redirect_uri的hash值来传递access_token值.如:\nhttp://localhost:7777/spring-oauth-client/implicit#access_token=dc891f4a-ac88-4ba6-8224-a2497e013865&token_type=bearer&expires_in=43199\n然后客户端通过JS等从hash值中取到access_token值.',
  `authorities` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '@PreAuthorize(\"hasAuthority(\'admin\')\")可以在方法上标志 用户或者说client 需要说明样的权限\n\n指定客户端所拥有的Spring Security的权限值,可选, 若有多个权限值,用逗号(,)分隔, 如: \"ROLE_UNITY,ROLE_USER\".\n对于是否要设置该字段的值,要根据不同的grant_type来判断, 若客户端在Oauth流程中需要用户的用户名(username)与密码(password)的(authorization_code,password),\n则该字段可以不需要设置值,因为服务端将根据用户在服务端所拥有的权限来判断是否有权限访问对应的API.\n但如果客户端在Oauth流程中不需要用户信息的(implicit,client_credentials),\n则该字段必须要设置对应的权限值, 因为服务端将根据该字段值的权限来判断是否有权限访问对应的API.\n\n(请在spring-oauth-client项目中来测试不同grant_type时authorities的变化)',
  `access_token_validity` int NULL DEFAULT NULL COMMENT '设定客户端的access_token的有效时间值(单位:秒),可选, 若不设定值则使用默认的有效时间值(60 * 60 * 12, 12小时).\n在服务端获取的access_token JSON数据中的expires_in字段的值即为当前access_token的有效时间值.\n在项目中, 可具体参考DefaultTokenServices.java中属性accessTokenValiditySeconds.\n在实际应用中, 该值一般是由服务端处理的, 不需要客户端自定义.',
  `refresh_token_validity` int NULL DEFAULT NULL COMMENT '设定客户端的refresh_token的有效时间值(单位:秒),可选, 若不设定值则使用默认的有效时间值(60 * 60 * 24 * 30, 30天).\n若客户端的grant_type不包括refresh_token,则不用关心该字段 在项目中, 可具体参考DefaultTokenServices.java中属性refreshTokenValiditySeconds.\n在实际应用中, 该值一般是由服务端处理的, 不需要客户端自定义',
  `additional_information` varchar(4968) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '这是一个预留的字段,在Oauth的流程中没有实际的使用,可选,但若设置值,必须是JSON格式的数据,如:\n{\"country\":\"CN\",\"country_code\":\"086\"}\n按照spring-security-oauth项目中对该字段的描述\nAdditional information for this client, not need by the vanilla OAuth protocol but might be useful, for example,for storing descriptive information.\n(详见ClientDetails.java的getAdditionalInformation()方法的注释) 在实际应用中, 可以用该字段来存储关于客户端的一些其他信息,如客户端的国家,地区,注册时的IP地址等等.',
  `autoapprove` varchar(256) CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci NULL DEFAULT NULL COMMENT '设置用户是否自动Approval操作, 默认值为 \'false\', 可选值包括 \'true\',\'false\', \'read\',\'write\'.\n该字段只适用于grant_type=\"authorization_code\"的情况,当用户登录成功后,若该值为\'true\'或支持的scope值,则会跳过用户Approve的页面, 直接授权.',
  `create_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间（自己添加不属于oauth2）',
  `update_time` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间（自己添加不属于oauth2）',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '数据是否删除： 0-未删除，1-删除（自己添加不属于oauth2）',
  PRIMARY KEY (`client_id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci COMMENT = 'oauth2的client表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Records of oauth_client_details
-- ----------------------------
INSERT INTO `oauth_client_details` VALUES ('system', '207cf410532f92a47dee245ce9b11ff71f578ebd763eb3bbea44ebd043d018fb', 'ouyunc', 'ouyunc-im', 'app', 'authorization_code,password,refresh_token', 'http://www.baidu.com', 'all', 3600, 7200, '{\"country\":\"CN\",\"country_code\":\"086\"}', 'false', '2020-06-30 10:05:28', '2023-06-15 15:14:56', 0);


-- ----------------------------
-- Table structure for ouyunc_im_app
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_app`;
CREATE TABLE `ouyunc_im_app`  (
  `id` bigint NOT NULL COMMENT '主键id',
  `app_key` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端（外部平台）key  唯一',
  `app_secret` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL COMMENT '客户端 （外部平台）secret',
  `app_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '客户端 （外部平台）name',
  `im_max_connections` bigint NOT NULL DEFAULT 0 COMMENT 'IM 最大连接数 大于等于-1： -1 - 无限制，',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除：0-未删除，1-已删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = 'im 客户端配置' ROW_FORMAT = Dynamic;


-- ----------------------------
-- Records of ouyunc_im_app
-- ----------------------------
INSERT INTO `ouyunc_im_app` VALUES (1, 'ouyunc', '123456', '偶云客', 1000, '2023-05-09 21:41:47', '2023-05-09 21:41:55', 0);


-- ----------------------------
-- Table structure for ouyunc_im_blacklist
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_blacklist`;
CREATE TABLE `ouyunc_im_blacklist`  (
  `id` bigint NOT NULL COMMENT '主键id',
  `identity` bigint NULL DEFAULT NULL COMMENT '群或客户端唯一标识',
  `user_id` bigint NULL DEFAULT NULL COMMENT '客户端id（被加入identity 黑名单）',
  `identity_type` tinyint(1) NULL DEFAULT NULL COMMENT '唯一标识类型，1-客户端唯一标识（用户），2-群唯一标识',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `identity_userId`(`identity`, `user_id`) USING BTREE COMMENT '关系唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '黑名单表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_friend
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_friend`;
CREATE TABLE `ouyunc_im_friend`  (
  `id` bigint NOT NULL COMMENT '主键id',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户id',
  `friend_user_id` bigint NULL DEFAULT NULL COMMENT '好友用户id',
  `friend_nick_name` varchar(20) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '好友昵称',
  `is_shield` tinyint(1) NULL DEFAULT NULL COMMENT '是否屏蔽该好友，0-未屏蔽，1-屏蔽',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `user_friend_user_id`(`user_id`, `friend_user_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '好友表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_group
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_group`;
CREATE TABLE `ouyunc_im_group`  (
  `id` bigint NOT NULL COMMENT '主键id',
  `group_name` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组名称',
  `group_avatar` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组头像',
  `group_description` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组描述',
  `group_announcement` varchar(200) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组公告',
  `group_join_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群加入策略：0-加群需要验证，1-加群自动同意',
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群状态，0-正常，1-异常（被平台封禁）',
  `mushin` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否全体禁言（群主和管理员除外），0-不禁言，1-禁言',
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
  `id` bigint NOT NULL COMMENT '主键id',
  `group_id` bigint NULL DEFAULT NULL COMMENT '群组id',
  `group_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '群组别名（该用户对这个群起的别名）',
  `user_id` bigint NULL DEFAULT NULL COMMENT '用户id',
  `is_leader` tinyint(1) NULL DEFAULT NULL COMMENT '是否是群主，0-否，1-是',
  `is_manager` tinyint(1) NULL DEFAULT NULL COMMENT '是否是群管理员，0-否，1-是',
  `user_nick_name` varchar(50) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '用户昵称（用户在群里的昵称）',
  `is_shield` tinyint(1) NULL DEFAULT NULL COMMENT '是否屏蔽群（不会接收到群的信息），0-未屏蔽，1-屏蔽',
  `mushin` tinyint(1) NULL DEFAULT NULL COMMENT '用户在群中的状态，0-未被禁言，1-被禁言',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`) USING BTREE,
  UNIQUE INDEX `group_user_id`(`user_id`, `group_id`) USING BTREE COMMENT '联合唯一索引'
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '群成员表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_message
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_message`;
CREATE TABLE `ouyunc_im_message`  (
  `id` bigint NOT NULL COMMENT '主键消息id',
  `protocol` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NULL DEFAULT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NULL DEFAULT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NULL DEFAULT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NULL DEFAULT NULL COMMENT '消息内容序列化算法',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送者ip',
  `from` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息发送者',
  `to` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `type` tinyint NULL DEFAULT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `content_type` tinyint NULL DEFAULT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `extra` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '扩展内容',
  `send_time` bigint NULL DEFAULT NULL COMMENT '消息发送时间戳',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息全量存储表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_read_receipt
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_read_receipt`;
CREATE TABLE `ouyunc_im_read_receipt`  (
  `id` bigint NOT NULL COMMENT '主键',
  `msg_id` bigint NULL DEFAULT NULL COMMENT '消息id，',
  `user_id` int NULL DEFAULT NULL COMMENT '已读消息的用户id',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息读已回执表' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_time_line
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_time_line`;
CREATE TABLE `ouyunc_im_time_line`  (
  `id` bigint NOT NULL COMMENT '主键消息id',
  `protocol` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议类型',
  `protocol_version` tinyint(1) NULL DEFAULT NULL COMMENT '消息协议版本号',
  `device_type` tinyint(1) NULL DEFAULT NULL COMMENT '设备类型',
  `network_type` tinyint(1) NULL DEFAULT NULL COMMENT '网络类型',
  `encrypt_type` tinyint(1) NULL DEFAULT NULL COMMENT '消息加密算法',
  `serialize_algorithm` tinyint(1) NULL DEFAULT NULL COMMENT '消息内容序列化算法',
  `ip` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '发送者ip',
  `from` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息发送者',
  `to` varchar(100) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息接收者/群组',
  `type` tinyint NULL DEFAULT NULL COMMENT '消息类型：心跳，群聊，私聊...',
  `content_type` tinyint NULL DEFAULT NULL COMMENT '消息内容类型: 文本，图片，音频...',
  `content` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息内容',
  `extra` varchar(1000) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NULL DEFAULT NULL COMMENT '消息扩展内容',
  `send_time` bigint NULL DEFAULT NULL COMMENT '消息发送时间戳',
  `withdraw` tinyint(1) NOT NULL DEFAULT 0 COMMENT '消息是否被撤回：0-未撤回，1-已撤回消息',
  `create_time` datetime NULL DEFAULT NULL COMMENT '创建时间（消息到达服务器时间）',
  `update_time` datetime NULL DEFAULT NULL COMMENT '修改时间',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '默认删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE,
  INDEX `to_from`(`to`, `from`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '消息信箱' ROW_FORMAT = Dynamic;

-- ----------------------------
-- Table structure for ouyunc_im_user
-- ----------------------------
DROP TABLE IF EXISTS `ouyunc_im_user`;
CREATE TABLE `ouyunc_im_user`  (
  `id` bigint NOT NULL COMMENT '主键id',
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
  `status` tinyint(1) NOT NULL DEFAULT 0 COMMENT '用户状态：0-正常，1-异常（被平台封禁）',
  `robot` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否是机器人：0-不是，1-是',
  `trusteeship` tinyint UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否托管（如果托管就会有服务端按照一定的策略进行与客户对话）：0-未托管，1-托管',
  `create_time` datetime NOT NULL COMMENT '创建时间',
  `update_time` datetime NOT NULL COMMENT '修改时间',
  `friend_join_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '好友添加的应答策略：0-需要验证，1-自动通过',
  `group_invite_policy` tinyint(1) NOT NULL DEFAULT 0 COMMENT '群邀请的应答策略：0-需要验证，1-自动通过',
  `deleted` tinyint(1) NOT NULL DEFAULT 0 COMMENT '是否删除，1-已删除，0-未删除',
  PRIMARY KEY (`id`) USING BTREE
) ENGINE = InnoDB CHARACTER SET = utf8mb4 COLLATE = utf8mb4_general_ci COMMENT = '用户表' ROW_FORMAT = Dynamic;

SET FOREIGN_KEY_CHECKS = 1;
