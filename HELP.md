# 偶云客IM
## redis 缓存key  
(1) opsvalue总的用户信息缓存:     ouyunc:im:user:${用户唯一标识}                         ImUser
(1) opsvalue登录用户信息:     ouyunc:im:user:login:${用户唯一标识}:${登录设备号}             LoginUserInfo
(1) opsvalue群信息:     ouyunc:im:user:group:${群唯一标识}                   ImGroup

(1) hash联系人（好友）:    ouyunc:im:user:contact:friend:${用户唯一标识}              ${联系人唯一标识}    ImFriendBO           

(1) hash群联系人（群）:    ouyunc:im:user:contact:group:${用户唯一标识}              ${群唯一标识}    ImGroup
(1) hash群（成员）:       ouyunc:im:user:group:${群组唯一标识}:members        ${群成员唯一标识}    ImGroupUserBO

(1) hash 群组黑名单:    ouyunc:im:black-list:group:${群组唯一标识}              ${用户唯一标识}    ImBlacklistBO
(1) hash 好友黑名单:    ouyunc:im:black-list:user:${好友唯一标识}              ${用户唯一标识}    ImBlacklistBO



(2) zset发件箱消息:   ouyunc:im:message:send:${用户唯一标识}                historyPacket     packetId
(3) zset收件箱消息:   ouyunc:im:message:receive:${用户/群唯一标识}             packet            packetId
(4) zset离线消息:   ouyunc:im:message:offline:${用户唯一标识}               packet            packetId

(5) zset全局失败消息:     ouyunc:im:message:fail:from:${from}:to:${to}            missPacket            packetId


(4) hash服务离线:   ouyunc:im:cluster:server:offline                  ${服务唯一标识}     set<String>

