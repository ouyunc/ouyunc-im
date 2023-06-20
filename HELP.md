# 偶云客IM
## redis 缓存key  
(1) opsvalue总的用户信息缓存:     ouyunc:im:user:${用户唯一标识}                         ImUser
(1) hash登录用户信息:     ouyunc:im:user:login:${用户唯一标识}             ${登录设备号}             LoginUserInfo
(1) opsvalue群信息:     ouyunc:im:user:group:${群唯一标识}                   ImGroup

(1) hash联系人（好友）:    ouyunc:im:user:contact:friend:${用户唯一标识}              ${联系人唯一标识}    ImFriendBO           
(1) hash群（成员）:       ouyunc:im:user:group:${群组唯一标识}:members        ${群成员唯一标识}    ImGroupUserBO

(1) hash 群组黑名单:    ouyunc:im:black-list:group:${群组唯一标识}              ${用户唯一标识}    ImBlacklistBO
(1) hash 好友黑名单:    ouyunc:im:black-list:user:${用户唯一标识}              ${好友唯一标识}    ImBlacklistBO

(1) zset好友请求    ouyunc:im:message:friend-request:${from}         packet     ${消息时间戳}                    
(1) zset群请求    ouyunc:im:message:group-request:${groupId}         packet     ${消息时间戳}


(2) zset发件箱消息:   ouyunc:im:message:send:${用户唯一标识}                packet     ${消息时间戳}
(3) zset收件箱消息:   ouyunc:im:message:receive:${用户/群唯一标识}             packet            ${消息时间戳}
(4) zset离线消息:   ouyunc:im:message:offline:${用户唯一标识}               packet            ${消息时间戳}

(5) zset全局失败消息:     ouyunc:im:message:fail:from:${from}:to:${to}            missPacket            ${消息时间戳}
(1) hash 已读消息:    ouyunc:im:message:read-receipt:${消息id}              ${用户唯一标识}    ImUser


(4) hash服务离线:   ouyunc:im:cluster:server:offline                  ${服务唯一标识}     set<String>

(1) hash存储 saas 中 im 连接数： ouyunc:im:app:${appKey}:connection             ${用户登录的唯一标识}  用户登录信息
(1) opsvalue存储 saas 中 im app 连接信息： ouyunc:im:app:${appKey}   ImAppDetail




4.0 版本更新内容：
1，packet 中extra 字段处理  (ok)；
2，提取出公共的集群路由逻辑 (ok)
3，新增集群路由协议，路由时通过该协议进行传输 （暂不处理）
4，验证原始集群中路由是否走pre处理器 （ok， 从channel pool 池中获取，已经跟协议无关）
5，优化数据库操作
6，去除离线消息处理
7, 配置在线支持设备以及消息过期时间
8，写定时任务去删除过期的消息
9, js-SDK 修改TextEncode 以及雪花算法 bigInt其他浏览器不支持问题 (ok)
10, 添加好友或群邀请，自动应答策略 (ok)
11, 去除hutool ,使用fastJson以及其他工具类来替代hutool (ok) 
12, 修改协议，去除ip,改成从本地获取客户端ip (暂不处理)
13，修改登录缓存标识为hash, key（登录用户id）， key1(登录设备号) value  (ok)
14, 增加登录设备类型
