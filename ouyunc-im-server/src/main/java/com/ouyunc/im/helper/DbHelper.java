package com.ouyunc.im.helper;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.im.cache.l1.distributed.redis.RedisDistributedL1Cache;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.DbSqlConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.db.operator.DbOperator;
import com.ouyunc.im.db.operator.MysqlDbOperator;
import com.ouyunc.im.domain.ImGroup;
import com.ouyunc.im.domain.ImSendMessage;
import com.ouyunc.im.domain.ImUser;
import com.ouyunc.im.domain.bo.ImBlacklistBO;
import com.ouyunc.im.domain.bo.ImFriendBO;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.lock.DistributedLock;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.OfflineContent;
import com.ouyunc.im.utils.SnowflakeUtil;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据库操作
 */
public class DbHelper {

    /**
     * redis 缓存操作类
     */
    private static RedisDistributedL1Cache<String, Object> cacheOperator = new RedisDistributedL1Cache<>();

    /**
     * 数据库操作类
     */
    private static DbOperator dbOperator = new MysqlDbOperator();


    /**
     * 处理消息已读回执，开线程处理
     * @param from 发送者唯一标识
     * @param to 代表用户或群的唯一标识
     * @param packetIdList
     * @return
     */
    @DistributedLock
    public static Set<ImSendMessage> messageReadReceipt(String from, String to, Set<Long> packetIdList) {
        // 记录已经修改的历史消息
        Set<ImSendMessage> sendMessageList = new HashSet<>();
        // 根据消息id查询服务器上的消息，然后进行更新
        for (Long packetId : packetIdList) {
            // 更新缓存，从信箱中取出相关消息
            Set<Object> objects = cacheOperator.rangeByScore(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + to, packetId, packetId, 0, 1);
            if (CollectionUtil.isNotEmpty(objects)) {
                ImSendMessage sendMessage = (ImSendMessage) objects.iterator().next();
                String readListStr = sendMessage.getReadList();
                Set<String> readList = JSONUtil.toBean(readListStr, Set.class);
                readList.add(from);
                ImSendMessage newSendMessage = new ImSendMessage();
                BeanUtil.copyProperties(sendMessage, newSendMessage);
                newSendMessage.setReadList(JSONUtil.toJsonStr(readList));
                // 先添加，后删除
                cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + to, newSendMessage, packetId);
                cacheOperator.removeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + to, sendMessage);
                sendMessageList.add(sendMessage);
            }
        }
        return sendMessageList;

    }




    /**
     * 添加原始消息到数据库
     * @param packet
     */
    public static void addMessage(Packet packet) {
        Message message = (Message) packet.getMessage();
        String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        dbOperator.insert(DbSqlConstant.MYSQL.INSERT_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), JSONUtil.toJsonStr(message.getContent()), message.getCreateTime(), nowDateTime, nowDateTime);
    }

    /**
     * 获取离线消息
     * @param message
     * @return
     */
    public static List<Packet> pullOfflineMessage(Message message) {
        List<Packet> packetList = new ArrayList<>();
        // 判断是按需来取还是全量拉取
        String to = message.getTo();
        OfflineContent offlineContent = JSONUtil.toBean(message.getContent(), OfflineContent.class);
        List<Long> packetIdList = offlineContent.getPacketList();
        // 如果传过来的消息id不为空，则可能是第N次拉取，从离线消息中删除消息
        if (CollectionUtil.isNotEmpty(packetIdList)) {
            for (Long packetId : packetIdList) {
                Set<Object> objects = cacheOperator.rangeByScore(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), packetId, packetId);
                if (CollectionUtil.isNotEmpty(objects)) {
                    Packet packet = (Packet) objects.iterator().next();
                    cacheOperator.removeZset(CacheConstant.OFFLINE + message.getFrom(), packet);
                }
            }
        }

        // 全量顺序拉取
        if (StrUtil.isBlank(to)) {
            Set<Object> packetSet = cacheOperator.rangeByScore(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), offlineContent.getPullPacketId(), offlineContent.getPullPacketId(), 0, 1);
            if (CollectionUtil.isNotEmpty(packetSet)) {
                Packet packet = (Packet) packetSet.iterator().next();
                Long rank = cacheOperator.reverseRank(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), packet);
                Set<Object> packetSetResult = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), rank, rank + offlineContent.getPullSize());
                Iterator<Object> iterator = packetSetResult.iterator();
                while (iterator.hasNext()) {
                    Packet packet0 = (Packet) iterator.next();
                    packetList.add(packet0);
                }
            }
            return packetList;
        }
        // 按需拉取,先查出所有，然后过滤前几条给客户端
        Set<Object> packetAllSet = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), 0, -1);
        if (CollectionUtil.isNotEmpty(packetAllSet)) {
            Iterator<Object> iterator = packetAllSet.iterator();
            while (iterator.hasNext()) {
                Packet packet0 = (Packet) iterator.next();
                Message message0 = (Message) packet0.getMessage();
                if (packetList.size() > offlineContent.getPullSize()) {
                    break;
                }
                if (message0.getFrom().equals(message.getTo())) {
                    packetList.add(packet0);
                }
            }
        }
        return packetList;
    }

    /**
     * 绑定好友关系,只要一方删除好友，双方的联系人列表都会删除,所以只需获取一方是否是好友就可以
     * @param from
     * @param to
     */
    @DistributedLock
    public static void bindFriend(String from, String to) {
        // 首先查询两个人是否是好友，如果不是好友则添加，如果是好友则不做处理
        ImFriendBO imFriendBO = (ImFriendBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to);
        // 已经是好友了
        if (imFriendBO != null) {
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + from);
        if (fromUser == null) {
            fromUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, from);
        }
        ImUser toUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + to);
        if (toUser == null) {
            toUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, to);
        }
        // 绑定关系
        if (fromUser != null && toUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            List<Object[]> argsList = new ArrayList<>();
            argsList.add(new Object[]{SnowflakeUtil.nextId(), fromUser.getId(), toUser.getId(), toUser.getNickName(), IMConstant.NOT_SHIELD, nowDateTime,nowDateTime});
            argsList.add(new Object[]{SnowflakeUtil.nextId(), toUser.getId(), fromUser.getId(), fromUser.getNickName(), IMConstant.NOT_SHIELD, nowDateTime,nowDateTime});
            dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_FRIEND.sql(),argsList);
            // 添加到缓存，好友联系人
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to, new ImFriendBO(fromUser.getId(), toUser.getId(), toUser.getNickName(), toUser.getUsername(), toUser.getEmail(), toUser.getPhoneNum(), toUser.getIdCardNum(), toUser.getAvatar(), toUser.getMotto(), toUser.getAge(), toUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + to, from, new ImFriendBO(toUser.getId(), fromUser.getId(), fromUser.getNickName(), fromUser.getUsername(), fromUser.getEmail(), fromUser.getPhoneNum(), fromUser.getIdCardNum(), fromUser.getAvatar(), fromUser.getMotto(), fromUser.getAge(), fromUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
        }

    }

    /**
     * 加群
     * @param from 客户端唯一标识
     * @param groupId  群唯一标识
     */
    public static void joinGroup(String from, String groupId) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
        // 已经存在群成员中
        if (imGroupUserBO != null) {
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + from);
        if (fromUser == null) {
            fromUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, from);
        }
        if (fromUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            // 存入数据库
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_GROUP_USER.sql(), SnowflakeUtil.nextId(), groupId, from, fromUser.getNickName(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, nowDateTime);
            // 放入缓存
            imGroupUserBO = new ImGroupUserBO(Long.valueOf(groupId), fromUser.getId(), fromUser.getUsername(), fromUser.getNickName(),fromUser.getEmail(),fromUser.getPhoneNum(),fromUser.getIdCardNum(),fromUser.getAvatar(),fromUser.getMotto(),fromUser.getAge(),fromUser.getSex(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, nowDateTime);
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from, imGroupUserBO);
        }

    }

    /**
     * @Author fangzhenxun
     * @Description 将to 移除群（剔除群）
     * @param to
     * @param groupId
     * @return void
     */
    public static void removeOutGroup(String to, String groupId) {
        dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, to);
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, to);
    }


    /**
     * 解散群
     * @param groupId
     */
    public static void disbandGroup(String groupId) {
        dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP.sql(), groupId);
        dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_ALL_USER.sql(), groupId);
        cacheOperator.deleteHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS);
    }


    /**
     * @Author fangzhenxun
     * @Description 退出群
     * @param from
     * @param groupId
     * @return void
     */
    public static void exitGroup(String from, String groupId) {
        dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, from);
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
    }

    /**
     * 根据用户唯一标识获取，用户信息
     * @param identity
     * @return
     */
    public static ImUser getUser(String identity) {
        ImUser imUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + identity);
        if (imUser == null){
            // 查询数据库
            imUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, identity);
        }
        return imUser;
    }

    /**
     * 根据唯一标识获取群信息
     * @param identity
     * @return
     */
    public static ImGroup getGroup(String identity) {
        ImGroup imGroup = (ImGroup) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + identity);
        if (imGroup == null) {
            imGroup = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP.sql(), ImGroup.class, identity);
        }
        return imGroup;
    }

    /**
     * 获取from的好友to的关系
     * @param from
     * @param to
     * @return
     */
    public static ImFriendBO getFriend(String from, String to) {
        ImFriendBO imFriendBO = (ImFriendBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to);
        if (imFriendBO == null) {
            imFriendBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_FRIEND_USER.sql(), ImFriendBO.class, from, to);
        }
        return imFriendBO;
    }


    /**
     * 获取from在to中的用户信息,该用户在群中的信息
     * @param from 发送者
     * @param to 群唯一标识
     * @return
     */
    public static ImGroupUserBO getGroupMember(String from, String to) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS, from);
        if (imGroupUserBO == null) {
            imGroupUserBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP_USER.sql(), ImGroupUserBO.class, from, to);
        }
        return imGroupUserBO;
    }


    /**
     * 根据群组id，返回群组中，当前所有成员
     * @param to
     * @param isGroupManager  是群管理员（包括群主）
     * @return
     */
    public static List<ImGroupUserBO> getGroupMembers(String to, boolean isGroupManager) {
        List<ImGroupUserBO> imUserList = new ArrayList<>();
        Map<String, Object> groupUserBOMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS);
        if (MapUtil.isNotEmpty(groupUserBOMap)) {
            for (Map.Entry<String, Object> entry : groupUserBOMap.entrySet()) {
                ImGroupUserBO imGroupUser = (ImGroupUserBO) entry.getValue();
                if (isGroupManager) {
                    if ((IMConstant.GROUP_MANAGER.equals(imGroupUser.getIsManager()) || IMConstant.GROUP_MANAGER.equals(imGroupUser.getIsLeader()))) {
                        imUserList.add(imGroupUser);
                    }
                }else {
                    imUserList.add(imGroupUser);
                }
            }
            return imUserList;
        }
        // 从数据库中查询,群成员
        imUserList = dbOperator.batchSelect(isGroupManager ? DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USERS.sql():DbSqlConstant.MYSQL.SELECT_GROUP_USERS.sql(), ImGroupUserBO.class, to);
        if (CollectionUtil.isNotEmpty(imUserList)) {
            Map<Object, ImGroupUserBO> groupUserMap = new HashMap<>();
            imUserList.forEach(groupUser ->{
                groupUserMap.put(groupUser.getUserId(), groupUser);
            });
            cacheOperator.putHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS, groupUserMap);
        }
        return imUserList;
    }

    /**
     * 根据群组id，返回群组中，当前所有成员
     * @param to 群组唯一标识
     * @return
     */
    public static List<ImGroupUserBO> getGroupMembers(String to) {
        return getGroupMembers(to, false);
    }

    /**
     * @Author fangzhenxun
     * @Description 获取该群的群主信息
     * @param groupId
     * @return com.ouyunc.im.domain.bo.ImGroupUserBO
     */
    public static ImGroupUserBO getGroupLeader(String groupId) {
        Map<String, Object> groupUserBOMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS);
        if (MapUtil.isNotEmpty(groupUserBOMap)) {
            for (Map.Entry<String, Object> entry : groupUserBOMap.entrySet()) {
                ImGroupUserBO imGroupUser = (ImGroupUserBO) entry.getValue();
                if (IMConstant.GROUP_LEADER.equals(imGroupUser.getIsLeader())) {
                    return imGroupUser;
                }
            }
        }
        return dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USER.sql(), ImGroupUserBO.class, groupId);
    }

    /**
     * 获取from 在to 中的黑名单信息
     * @param from
     * @param to
     * @param type 1-用户的黑名单，2-群组的黑名单
     * @return
     */
    public static ImBlacklistBO getBackList(String from, String to, Integer type) {
        if (IMConstant.USER_TYPE_1.equals(type)) {
            ImBlacklistBO imBlacklistBO = (ImBlacklistBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.BLACK_LIST +  CacheConstant.USER + to, from);
            if (imBlacklistBO == null) {
                // 判断是否是好友
                ImFriendBO friend = getFriend(from, to);
                imBlacklistBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_BLACK_LIST.sql(), ImBlacklistBO.class, to, type, from);
                if (imBlacklistBO != null && friend != null) {
                    // 是好友
                    imBlacklistBO.setNickName(friend.getFriendNickName());
                }
                // 不是好友
                return imBlacklistBO;
            }
            return imBlacklistBO;
        }
        // 群组黑名单
        if (IMConstant.GROUP_TYPE_2.equals(type)) {
            ImBlacklistBO imBlacklistBO = (ImBlacklistBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.BLACK_LIST +  CacheConstant.GROUP + to, from);
            if (imBlacklistBO == null) {
                // 判断该用户是否在群组中
                ImGroupUserBO groupMember = getGroupMember(from, to);
                imBlacklistBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_BLACK_LIST.sql(), ImBlacklistBO.class, to, type, from);
                if (imBlacklistBO != null && groupMember != null) {
                    // 是好友
                    imBlacklistBO.setNickName(groupMember.getUserNickName());
                }
            }
            return imBlacklistBO;
        }
        return null;
    }


    /**
     * @Author fangzhenxun
     * @Description 将消息写到from的 发件箱
     * @param packet
     * @param from
     * @return void
     */
    public static void write2SendTimeline(Packet packet, String from) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + from, packet, packet.getPacketId());
    }

    /**
     * @Author fangzhenxun
     * @Description 将消息写到to 的收件箱
     * @param packet
     * @param to
     * @return void
     */
    public static void write2ReceiveTimeline(Packet packet, String to) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.RECEIVE + to, packet, packet.getPacketId());
    }

    /**
     * 存入离线消息
     * @param to 消息发送者
     * @param packet
     */
    public static void write2OfflineTimeline(Packet packet, String to) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + to, packet, packet.getPacketId());
    }
}
