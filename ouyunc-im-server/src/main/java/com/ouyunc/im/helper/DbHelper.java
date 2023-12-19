package com.ouyunc.im.helper;


import com.ouyunc.im.cache.l1.distributed.redis.RedisDistributedL1Cache;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.DbSqlConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.db.operator.DbOperator;
import com.ouyunc.im.db.operator.MysqlDbOperator;
import com.ouyunc.im.domain.*;
import com.ouyunc.im.domain.bo.ImBlacklistBO;
import com.ouyunc.im.domain.bo.ImFriendBO;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.ReadReceiptContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.MapUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import io.netty.handler.codec.mqtt.MqttTopicSubscription;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 数据库操作
 */
public class DbHelper {
    private static final Logger log = LoggerFactory.getLogger(DbHelper.class);

    /**
     * redis 缓存操作类
     */
    public static RedisDistributedL1Cache<String, Object> cacheOperator = new RedisDistributedL1Cache<>();

    /**
     * 数据库操作类
     */
    private static DbOperator dbOperator = new MysqlDbOperator();


    /**
     * 根据appKey 获取app 详情
     */
    public static ImApp getApp(String appKey) {
        ImApp app = (ImApp) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.APP + appKey);
        if (app == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            // 从数据库查询
            app = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_IM_APP_DETAIL.sql(), ImApp.class, appKey);
            if (app != null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP + appKey, app);
            }
        }
        return app;
    }


    /**
     * 绑定好友关系,只要一方删除好友，双方的联系人列表都会删除,所以只需获取一方是否是好友就可以
     *
     * @param from
     * @param to
     */
    public static void bindFriend(String appKey, String from, String to, long currentTimestamp) {
        ImUser fromUser = getUser(appKey, from);
        ImUser toUser = getUser(appKey, to);
        if (fromUser == null || toUser == null) {
            return;
        }
        // 绑定关系
        String nowDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(currentTimestamp), ZoneId.systemDefault()));
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            List<Object[]> argsList = new ArrayList<>();
            argsList.add(new Object[]{SnowflakeUtil.nextId(), fromUser.getId(), toUser.getId(), toUser.getNickName(), IMConstant.NOT_SHIELD, appKey, nowDateTime, nowDateTime});
            argsList.add(new Object[]{SnowflakeUtil.nextId(), toUser.getId(), fromUser.getId(), fromUser.getNickName(), IMConstant.NOT_SHIELD, appKey, nowDateTime, nowDateTime});
            dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_FRIEND.sql(), argsList);
        }
        // 添加到缓存，好友联系人
        cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to, new ImFriendBO(fromUser.getId().toString(), appKey, toUser.getId().toString(), toUser.getNickName(), toUser.getUsername(), toUser.getEmail(), toUser.getPhoneNum(), toUser.getIdCardNum(), toUser.getAvatar(), toUser.getMotto(), toUser.getAge(), toUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
        cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + to, from, new ImFriendBO(toUser.getId().toString(), appKey, fromUser.getId().toString(), fromUser.getNickName(), fromUser.getUsername(), fromUser.getEmail(), fromUser.getPhoneNum(), fromUser.getIdCardNum(), fromUser.getAvatar(), fromUser.getMotto(), fromUser.getAge(), fromUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
    }

    /**
     * 加群/绑定群
     *
     * @param from    客户端唯一标识
     * @param groupId 群唯一标识
     */
    public static void bindGroup(String appKey, String from, String groupId) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
        // 已经存在群成员中
        if (imGroupUserBO != null) {
            return;
        }
        // 获取群信息
        ImGroup group = getGroup(appKey, groupId);
        if (group == null) {
            log.error("在绑定群关系中，获取群信息失败！");
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = getUser(appKey, from);
        if (fromUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
                // 存入数据库
                dbOperator.insert(DbSqlConstant.MYSQL.INSERT_GROUP_USER.sql(), SnowflakeUtil.nextId(), groupId, from, group.getGroupName(), fromUser.getNickName(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, appKey, nowDateTime);
            }
            // 放入缓存
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from, new ImGroupUserBO(appKey, groupId, fromUser.getId().toString(), fromUser.getUsername(), fromUser.getNickName(), group.getGroupName(), fromUser.getEmail(), fromUser.getPhoneNum(), fromUser.getIdCardNum(), fromUser.getAvatar(), fromUser.getMotto(), fromUser.getAge(), fromUser.getSex(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, nowDateTime));
        }

    }

    /**
     * @param to
     * @param groupId
     * @return void
     * @Author fangzhenxun
     * @Description 将to 移除群（剔除群）
     */
    public static void removeOutGroup(String appKey, String to, String groupId) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, to);
        }
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, to);
    }


    /**
     * 解散群
     *
     * @param groupId
     */
    public static void disbandGroup(String appKey, String groupId) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP.sql(), groupId);
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_ALL_USER.sql(), groupId);
        }
        cacheOperator.delete(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId);
        cacheOperator.deleteHashAll(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS);
    }


    /**
     * @param from
     * @param groupId
     * @return void
     * @Author fangzhenxun
     * @Description 退出群
     */
    public static void exitGroup(String appKey, String from, String groupId) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, from);
        }
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
    }

    /**
     * 根据用户唯一标识获取，用户信息
     *
     * @param identity
     * @return
     */
    public static ImUser getUser(String appKey, String identity) {
        ImUser imUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.USER + identity);
        if (imUser == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            // 查询数据库
            imUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, identity);
            if (imUser != null) {
                // 放入缓存
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.USER + identity, imUser);
            }
        }
        return imUser;
    }

    /**
     * 根据唯一标识获取群信息
     *
     * @param identity
     * @return
     */
    public static ImGroup getGroup(String appKey, String identity) {
        ImGroup imGroup = (ImGroup) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + identity);
        if (imGroup == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imGroup = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP.sql(), ImGroup.class, identity);
            if (imGroup != null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + identity, imGroup);
            }
        }
        return imGroup;
    }

    /**
     * 获取from的好友to的关系
     *
     * @param from
     * @param to
     * @return
     */
    public static ImFriendBO getFriend(String appKey, String from, String to) {
        ImFriendBO imFriendBO = (ImFriendBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to);
        if (imFriendBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imFriendBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_FRIEND_USER.sql(), ImFriendBO.class, from, to);
            if (imFriendBO != null) {
                cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to, imFriendBO);
            }
        }
        return imFriendBO;
    }


    /**
     * 获取from在groupId中的用户信息,该用户在群中的信息
     *
     * @param from    发送者
     * @param groupId 群唯一标识
     * @return
     */
    public static ImGroupUserBO getGroupMember(String appKey, String from, String groupId) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
        if (imGroupUserBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imGroupUserBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP_USER.sql(), ImGroupUserBO.class, groupId, from);
            if (imGroupUserBO != null) {
                cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from, imGroupUserBO);
            }
        }
        return imGroupUserBO;
    }


    /**
     * 根据群组id，返回群组中，当前所有成员
     *
     * @param to             群id
     * @param isGroupManager 是群管理员（包括群主）
     * @return
     */
    public static List<ImGroupUserBO> getGroupMembers(String appKey, String to, boolean isGroupManager) {
        List<ImGroupUserBO> imUserList = new ArrayList<>();
        Map<String, Object> groupUserBOMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS);
        if (MapUtil.isNotEmpty(groupUserBOMap)) {
            for (Map.Entry<String, Object> entry : groupUserBOMap.entrySet()) {
                ImGroupUserBO imGroupUser = (ImGroupUserBO) entry.getValue();
                if (isGroupManager) {
                    if ((IMConstant.GROUP_MANAGER.equals(imGroupUser.getIsManager()) || IMConstant.GROUP_LEADER.equals(imGroupUser.getIsLeader()))) {
                        imUserList.add(imGroupUser);
                    }
                } else {
                    imUserList.add(imGroupUser);
                }
            }
            return imUserList;
        }
        // 从数据库中查询,群成员
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imUserList = dbOperator.batchSelect(isGroupManager ? DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USERS.sql() : DbSqlConstant.MYSQL.SELECT_GROUP_USERS.sql(), ImGroupUserBO.class, to);
            if (CollectionUtils.isNotEmpty(imUserList)) {
                Map<Object, ImGroupUserBO> groupUserMap = new HashMap<>();
                imUserList.forEach(groupUser -> {
                    groupUserMap.put(groupUser.getUserId(), groupUser);
                });
                cacheOperator.putHashAll(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS, groupUserMap);
            }
        }
        return imUserList;
    }

    /**
     * 根据群组id，返回群组中，当前所有成员
     *
     * @param groupId 群组唯一标识
     * @return
     */
    public static List<ImGroupUserBO> getGroupMembers(String appKey, String groupId) {
        return getGroupMembers(appKey, groupId, false);
    }

    /**
     * @param groupId
     * @return com.ouyunc.im.domain.bo.ImGroupUserBO
     * @Author fangzhenxun
     * @Description 获取该群的群主信息
     */
    public static ImGroupUserBO getGroupLeader(String appKey, String groupId) {
        Map<String, Object> groupUserBOMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS);
        ImGroupUserBO imGroupUser = null;
        if (MapUtil.isNotEmpty(groupUserBOMap)) {
            for (Map.Entry<String, Object> entry : groupUserBOMap.entrySet()) {
                imGroupUser = (ImGroupUserBO) entry.getValue();
                if (IMConstant.GROUP_LEADER.equals(imGroupUser.getIsLeader())) {
                    return imGroupUser;
                }
            }
        }
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imGroupUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USER.sql(), ImGroupUserBO.class, groupId);
        }
        return imGroupUser;
    }

    /**
     * 获取from 在to 中的黑名单信息
     *
     * @param from
     * @param to
     * @param type 1-用户的黑名单，2-群组的黑名单
     * @return
     */
    public static ImBlacklistBO getBackList(String appKey, String from, String to, Integer type) {
        if (IMConstant.USER_TYPE_1.equals(type)) {
            ImBlacklistBO imBlacklistBO = (ImBlacklistBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM + CacheConstant.BLACK_LIST + CacheConstant.USER + to, from);
            if (imBlacklistBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
                // 判断是否是好友
                ImFriendBO friend = getFriend(appKey, from, to);
                imBlacklistBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_BLACK_LIST.sql(), ImBlacklistBO.class, to, type, from);
                if (imBlacklistBO != null && friend != null) {
                    // 是好友
                    imBlacklistBO.setNickName(friend.getFriendNickName());
                    cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM + CacheConstant.BLACK_LIST + CacheConstant.USER + to, from, imBlacklistBO);
                }
                // 不是好友
                return imBlacklistBO;
            }
            return imBlacklistBO;
        }
        // 群组黑名单
        if (IMConstant.GROUP_TYPE_2.equals(type)) {
            ImBlacklistBO imBlacklistBO = (ImBlacklistBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM + CacheConstant.BLACK_LIST + CacheConstant.GROUP + to, from);
            if (imBlacklistBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
                // 判断该用户是否在群组中
                ImGroupUserBO groupMember = getGroupMember(appKey, from, to);
                imBlacklistBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_BLACK_LIST.sql(), ImBlacklistBO.class, to, type, from);
                if (imBlacklistBO != null && groupMember != null) {
                    // 是好友
                    imBlacklistBO.setNickName(groupMember.getNickName());
                    cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM + CacheConstant.BLACK_LIST + CacheConstant.GROUP + to, from, imBlacklistBO);
                }
            }
            return imBlacklistBO;
        }
        return null;
    }


    /**
     * 从信箱中读取消息
     *
     * @param to
     * @param packetId
     */
    public static Packet readFromTimeline(String appKey, String to, long packetId) {
        // 从离线消息中
        Set<Packet> packetSet = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.TIME_LINE + to, 0, -1);
        if (CollectionUtils.isNotEmpty(packetSet)) {
            Optional<Packet> packetOptional = packetSet.parallelStream().filter(packet -> packetId == packet.getPacketId()).findFirst();
            // 如果存在则返回
            if (packetOptional.isPresent()) {
                return packetOptional.get();
            }
        }
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            ImTimeLine timeLinePacket = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_TIME_LINE.sql(), ImTimeLine.class, packetId);
            // 这里数据长度没有查出
            Message message = new Message(timeLinePacket.getFrom(), timeLinePacket.getTo(), timeLinePacket.getContentType(), timeLinePacket.getContent(), timeLinePacket.getExtra(), timeLinePacket.getSendTime());
            return new Packet(timeLinePacket.getProtocol(), timeLinePacket.getProtocolVersion(), timeLinePacket.getId(), timeLinePacket.getDeviceType(), timeLinePacket.getNetworkType(), timeLinePacket.getIp(), timeLinePacket.getType(), timeLinePacket.getEncryptType(), timeLinePacket.getSerializeAlgorithm(), message);
        }
        return null;
    }

    /**
     * 处理消息已读回执，开线程处理
     *
     * @param from            发送者唯一标识
     * @param readReceiptList
     * @return
     */
    public static void writeMessageReadReceipt(String appKey, String from, List<ReadReceiptContent> readReceiptList) {
        ImUser user = getUser(appKey, from);
        List<Object[]> batchArgs = new ArrayList<>();
        for (ReadReceiptContent readReceiptContent : readReceiptList) {
            batchArgs.add(new Object[]{SnowflakeUtil.nextId(), readReceiptContent.getPacketId(), from});
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.READ_RECEIPT + readReceiptContent.getPacketId(), from, user);
        }
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_READ_RECEIPT.sql(), batchArgs);
        }
    }

    /**
     * 添加原始消息到数据库
     *
     * @param packet
     */
    public static void writeMessage(String appKey, Packet packet) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            Message message = (Message) packet.getMessage();
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), message.getContent(), message.getExtra(), message.getCreateTime(), appKey, nowDateTime, nowDateTime);
        }
    }

    /**
     * @param packet
     * @param from
     * @param to
     * @param timestamp
     * @return void
     * @Author fangzhenxun
     * @Description 将消息信箱中(这里需要写两条消息, 发送者信箱中一条, 接受者信箱中一条 ; 每个用户都会有一个信箱用来接收消息和发送消息, 群组也一样)
     */
    public static void write2Timeline(String appKey, Packet packet, String from, String to, long timestamp) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            Message message = (Message) packet.getMessage();
            String nowDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_TIME_LINE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), message.getContent(), message.getExtra(), message.getCreateTime(), IMConstant.NOT_WITHDRAW, appKey, nowDateTime, nowDateTime);
        }
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.TIME_LINE + from, packet, timestamp);
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.TIME_LINE + to, packet, timestamp);
    }


    /**
     * 存入离线消息,注意： 离线消息原则上每个用户只存最近拉取的一条数据
     * 对于一个用户，在他登出后的离线期间内，肯定是所有的消息都没有收到的，完全不用对所有的每一条离线消息存储一个离线msg_id，而只需要存储最近一条拉取到的离线消息的time（或者msg_id），下次登录时拉取在那之后的所有群消息即可，而完全没有必要存储每个人未拉取到的离线消息msg_id
     * 这里要保证在客户上线的时候要去拉取离线消息中的数据并进行ack
     * 这里需要加锁，因为在收到消息后需要手动ack 来更新或删除该离线消息数据
     *
     * @param identity 消息发送者
     * @param packet
     */
    public static void write2OfflineTimeline(String appKey, Packet packet, String identity, long timestamp) {
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOCK + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + identity);
        try {
            lock.lock();
            Long count = cacheOperator.sizeZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + identity);
            if (count == null || count == 0) {
                cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + identity, packet.getPacketId(), timestamp);
            }
        } finally {
            lock.unlock();
        }
    }


    /**
     * 处理好友请求相关逻辑
     *
     * @param packet
     */
    public static void handleFriendRequest(String appKey, Packet packet, long timestamp) {
        Message message = (Message) packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        MessageContentEnum messageContentEnum = MessageContentEnum.prototype(message.getContentType());
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOCK + CacheConstant.FRIEND + CacheConstant.REFUSE_AGREE + IdentityUtil.sessionId(from, to));
        try {
            lock.lock();
            // 如果是好友直接返回
            ImFriendBO imFriendBO = getFriend(appKey, from, to);
            // 已经是好友了
            if (imFriendBO != null) {
                return;
            }
            // 处理对方同意的消息
            if (MessageContentEnum.FRIEND_AGREE.equals(messageContentEnum)) {
                bindFriend(appKey, to, from, timestamp);
            }
            // 将消息缓存到自己的信箱和别人的信箱各一份数据， 每个用户都有一个信箱（收件箱和发件箱），这里只是好友请求的收发件箱，不做落库处理
            cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.FRIEND_REQUEST + from, packet, timestamp);
            cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.FRIEND_REQUEST + to, packet, timestamp);
        } finally {
            lock.unlock();
        }
    }

    /**
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 处理撤回消息，删除离校消息的时候进行加锁
     */
    public static void handleWithdrawMessage(Packet packet, long timestamp) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            String nowDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
            dbOperator.update(DbSqlConstant.MYSQL.UPDATE_TIME_LINE.sql(), nowDateTime, packet.getPacketId());
        }
    }

    /**
     * @param packet
     * @param identity
     * @param now
     * @return void
     * @Author fangzhenxun
     * @Description 处理群请求消息
     */
    public static void handleGroupRequestMessage(String appKey, Packet packet, String identity, long now) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.IM_MESSAGE + CacheConstant.GROUP_REQUEST + identity, packet, now);
    }


    /**
     * @param topicName
     * @return com.ouyunc.im.domain.MqttTopic
     * @Author fangzhenxun
     * @Description 获取topic
     */
    public static MqttTopic getTopic(String appKey, String topicName) {
        MqttTopic mqttTopic = (MqttTopic) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicName);
        if (mqttTopic == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            // 查询数据库
            mqttTopic = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_MQTT_TOPIC.sql(), MqttTopic.class, appKey, topicName);
            if (mqttTopic != null) {
                // 放入缓存
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicName, mqttTopic);
            }

        }
        return mqttTopic;
    }

    /**
     * @param mqttTopicSubscription
     * @return void
     * @Author fangzhenxun
     * @Description mqtt 主题订阅
     */
    public static void mqttTopicSubscribe(String appKey, String clientId, MqttTopicSubscription mqttTopicSubscription, long timestamp) {
        String topicName = mqttTopicSubscription.topicName();
        MqttTopic mqttTopic = getTopic(appKey, topicName);
        MqttTopicSubscribe mqttTopicSubscribe = null;
        String nowDateTime = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").format(LocalDateTime.ofInstant(Instant.ofEpochMilli(timestamp), ZoneId.systemDefault()));
        long topicId = SnowflakeUtil.nextId();
        long topicSubscribeId = SnowflakeUtil.nextId();
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            if (mqttTopic == null) {
                dbOperator.insert(DbSqlConstant.MYSQL.INSERT_MQTT_TOPIC.sql(), topicId, appKey, topicName, null, nowDateTime);
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicName, new MqttTopic(topicId, appKey, topicName, null, nowDateTime, 0));
            }else {
                topicId = mqttTopic.getId();
            }
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_MQTT_TOPIC_SUBSCRIBE.sql(), topicSubscribeId, appKey, topicId, clientId, mqttTopicSubscription.option().qos().value(), mqttTopicSubscription.option().isNoLocal() ? 1 : 0, mqttTopicSubscription.option().isRetainAsPublished() ? 1 : 0, mqttTopicSubscription.option().retainHandling().value(), nowDateTime, nowDateTime);
        }
        if (!IMServerContext.SERVER_CONFIG.isDbEnable()) {
            if (mqttTopic == null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicName, new MqttTopic(topicId, appKey, topicName, null, nowDateTime, 0));
            }else {
                topicId = mqttTopic.getId();
            }
        }
        mqttTopicSubscribe = new MqttTopicSubscribe(topicSubscribeId, appKey, topicId, clientId, mqttTopicSubscription.option().qos().value(), mqttTopicSubscription.option().isNoLocal() ? 1 : 0, mqttTopicSubscription.option().isRetainAsPublished() ? 1 : 0, mqttTopicSubscription.option().retainHandling().value(), nowDateTime, nowDateTime, 0);
        cacheOperator.putHashIfAbsent(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.MQTT + CacheConstant.TOPIC + topicId, clientId, mqttTopicSubscribe);
    }
}
