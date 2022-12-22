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
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.db.operator.DbOperator;
import com.ouyunc.im.db.operator.MysqlDbOperator;
import com.ouyunc.im.domain.*;
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
     * 批量更新历史消息已读回执状态，开线程处理
     * @param from 发送者唯一标识
     * @param to 代表用户或群的唯一标识
     * @param packetIdList
     * @return
     */
    @DistributedLock
    public static Set<ImSendMessage> batchUpdateMessageReadReceiptStatus(String from, String to, Set<Long> packetIdList) {
        // 记录已经修改的历史消息
        Set<ImSendMessage> sendMessageList = new HashSet<>();
        String toHistoryTimelineIdentity = CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + to;
        // 根据消息id查询服务器上的消息，然后进行更新
        for (Long packetId : packetIdList) {
            // 先异步更新db
            if (IMServerContext.SERVER_CONFIG.isMessageDbEnable()) {
                // 更新历史信息表（更新发件箱信息数据）
                ImSendMessage imSendMessage = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_READ_RECEIPT.sql(), ImSendMessage.class, packetId);
                // set集合json
                Set<String> readList = JSONUtil.toBean(imSendMessage.getReadList(), Set.class);
                readList.add(from);
                // 更改信息，并存入数据库
                dbOperator.update(DbSqlConstant.MYSQL.UPDATE_READ_RECEIPT.sql(), JSONUtil.toJsonStr(readList) ,packetId);
                // 以数据库为准
                sendMessageList.add(imSendMessage);
            }
            // 更新缓存，从信箱中取出相关消息
            Set<Object> objects = cacheOperator.rangeByScore(toHistoryTimelineIdentity, packetId, packetId, 0, 1);
            if (CollectionUtil.isNotEmpty(objects)) {
                ImSendMessage sendMessage = (ImSendMessage) objects.iterator().next();
                String readListStr = sendMessage.getReadList();
                Set<String> readList = JSONUtil.toBean(readListStr, Set.class);
                readList.add(from);
                ImSendMessage newSendMessage = new ImSendMessage();
                BeanUtil.copyProperties(sendMessage, newSendMessage);
                newSendMessage.setReadList(JSONUtil.toJsonStr(readList));
                // 先添加，后删除
                cacheOperator.addZset(toHistoryTimelineIdentity, newSendMessage, packetId);
                cacheOperator.removeZset(toHistoryTimelineIdentity, sendMessage);

                // 如果未开启数据库
                if (!IMServerContext.SERVER_CONFIG.isMessageDbEnable()) {
                    sendMessageList.add(sendMessage);
                }
            }
        }
        return sendMessageList;

    }

    /**
     * 存入离线消息
     * @param to 消息发送者
     * @param packet
     */
    public static void addOfflineMessage(String to, Packet packet) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + to, packet, packet.getPacketId());
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
     * 根据群组id，返回群组中，当前所有成员
     * @param to
     * @param isGroupManager  是群管理员（包括群主）
     * @return
     */
    public static List<ImGroupUser> getGroupMembers(String to, boolean isGroupManager) {
        List<ImGroupUser> imUserList = new ArrayList<>();
        Map<String, Object> usersMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS);
        if (MapUtil.isNotEmpty(usersMap)) {
            for (Map.Entry<String, Object> entry : usersMap.entrySet()) {
                ImGroupUser imGroupUser = (ImGroupUser) entry.getValue();
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
        // 从数据库中查询
        imUserList = dbOperator.batchSelect(isGroupManager?DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USER.sql():DbSqlConstant.MYSQL.SELECT_GROUP_USER.sql(), ImGroupUser.class, to);
        if (CollectionUtil.isNotEmpty(imUserList)) {
            Map<Object, ImGroupUser> groupUserMap = new HashMap<>();
            imUserList.forEach(groupUser ->{
                groupUserMap.put(groupUser.getUserId(), groupUser);
            });
            cacheOperator.putHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS, groupUserMap);
        }
        return imUserList;
    }

    /**
     * 根据群组id，返回群组中，当前所有成员
     * @param to
     * @return
     */
    public static List<ImGroupUser> getGroupMembers(String to) {
        return getGroupMembers(to, false);
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
                Set<Object> objects = cacheOperator.rangeByScore(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), packetId, packetId);
                if (CollectionUtil.isNotEmpty(objects)) {
                    Packet packet = (Packet) objects.iterator().next();
                    cacheOperator.removeZset(CacheConstant.OFFLINE + message.getFrom(), packet);
                }
            }
        }


        // 全量顺序拉取
        if (StrUtil.isBlank(to)) {
            Set<Object> packetSet = cacheOperator.rangeByScore(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), offlineContent.getPullPacketId(), offlineContent.getPullPacketId(), 0, 1);
            if (CollectionUtil.isNotEmpty(packetSet)) {
                Packet packet = (Packet) packetSet.iterator().next();
                Long rank = cacheOperator.reverseRank(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), packet);
                Set<Object> packetSetResult = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), rank, rank + offlineContent.getPullSize());
                Iterator<Object> iterator = packetSetResult.iterator();
                while (iterator.hasNext()) {
                    Packet packet0 = (Packet) iterator.next();
                    packetList.add(packet0);
                }
            }
            return packetList;
        }
        // 按需拉取,先查出所有，然后过滤前几条给客户端
        Set<Object> packetAllSet = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + CacheConstant.COLON + message.getFrom(), 0, -1);
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
     * 绑定好友关系,只要一方删除好友，双方的联系人列表都会删除
     * @param from
     * @param to
     */
    @DistributedLock
    public static void bindFriend(String from, String to) {
        // 首先查询两个人是否是好友，如果不是好友则添加，如果是好友则不做处理
        ImFriend imFriend = (ImFriend) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to);
        // 已经是好友了
        if (imFriend != null) {
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + from);
        ImUser toUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + to);
        // 绑定关系
        if (fromUser != null && toUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            List<Object[]> argsList = new ArrayList<>();
            //id, user_id, friend_user_id, friend_nick_name, create_time
            argsList.add(new Object[]{SnowflakeUtil.nextId(), fromUser.getId(), toUser.getId(), toUser.getNickName(), nowDateTime});
            argsList.add(new Object[]{SnowflakeUtil.nextId(), toUser.getId(), fromUser.getId(), fromUser.getNickName(), nowDateTime});
            dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_FRIEND.sql(),argsList);
            // 添加到缓存，好友联系人
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to, null);
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + to, from, null);
        }

    }

    /**
     * 加群
     * @param from
     * @param to
     */
    public static void joinGroup(String from, String to) {


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
}
