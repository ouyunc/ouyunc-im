# 偶云客IM-v5.x
## redis 缓存key 
(1)  opsvalue app平台信息缓存： ouyunc:app:${appKey}                                ImApp
(2) opsvalue总的用户信息缓存:     ouyunc:app-key:${appKey}:user:${用户唯一标识}                         ImUser
(3) hash登录用户信息:     ouyunc:app-key:${appKey}:login:user:${用户唯一标识}             ${登录设备号}             LoginUserInfo
(4) opsvalue群信息:     ouyunc:app-key:${appKey}:im:user:group:${群唯一标识}                   ImGroup
(5) hash联系人（好友）:    ouyunc:app-key:${appKey}:im:user:contact:friend:${用户唯一标识}              ${联系人唯一标识}    ImFriendBO           
(6) hash群（成员）:       ouyunc:app-key:${appKey}:im:user:group:${群组唯一标识}:members        ${群成员唯一标识}    ImGroupUserBO
(7) hash 群组黑名单:    ouyunc:app-key:${appKey}:im:black-list:group:${群组唯一标识}              ${用户唯一标识}    ImBlacklistBO
(8) hash 好友黑名单:    ouyunc:app-key:${appKey}:im:black-list:user:${用户唯一标识}              ${好友唯一标识}    ImBlacklistBO
(9) zset好友请求    ouyunc:app-key:${appKey}:im:message:friend-request:${from}         packet     ${消息时间戳}                    
(10) zset群请求    ouyunc:app-key:${appKey}:im:message:group-request:${groupId}         packet     ${消息时间戳}
(11) zset信箱消息:   ouyunc:app-key:${appKey}:im:message:time-line:${用户唯一标识}                packet     ${消息时间戳}
(12) zset离线消息:   ouyunc:app-key:${appKey}:im:message:offline:${用户唯一标识}               packetId            ${消息时间戳}
(13) zset全局失败消息:     ouyunc:app-key:${appKey}:im:message:fail:from:${from}:to:${to}            missPacket            ${消息时间戳}
(14) hash 已读(读已回执)消息:    ouyunc:app-key:${appKey}:im:message:read-receipt:${消息id}              ${用户唯一标识}    ImUser
(15) hash存储 saas 中 im 连接数： ouyunc:app-key:${appKey}:connections             ${用户登录的唯一标识}  用户登录信息


