package com.ouyunc.im.helper;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.core.map.MapUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.im.cache.l1.distributed.redis.RedisDistributedL1Cache;
import com.im.cache.l1.distributed.redis.lettuce.RedisFactory;
import com.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.DbSqlConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.db.operator.DbOperator;
import com.ouyunc.im.db.operator.MysqlDbOperator;
import com.ouyunc.im.domain.ImAppDetail;
import com.ouyunc.im.domain.ImGroup;
import com.ouyunc.im.domain.ImUser;
import com.ouyunc.im.domain.bo.ImBlacklistBO;
import com.ouyunc.im.domain.bo.ImFriendBO;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.packet.message.content.OfflineContent;
import com.ouyunc.im.packet.message.content.ReadReceiptContent;
import com.ouyunc.im.packet.message.content.UnreadContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SnowflakeUtil;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据库操作
 */
public class  DbHelper {
    private static final Logger log = LoggerFactory.getLogger(DbHelper.class);

    /**
     * redis 缓存操作类
     */
    private static RedisDistributedL1Cache<String, Object> cacheOperator = new RedisDistributedL1Cache<>();

    /**
     * 数据库操作类
     */
    private static DbOperator dbOperator = new MysqlDbOperator();








    /**
     * 获取离线消息
     * @param message
     * @return
     * 将在4.0版本去除
     */
    @Deprecated
    public static List<Packet<Message>> pullOfflineMessage(Message message) {
        List<Packet<Message>> result = new ArrayList<>();
        if (MessageContentEnum.UNREAD_CONTENT.type() == message.getContentType()) {
            List<UnreadContent> unreadContentList = new ArrayList<>();
            Set<Packet<Message>> packetSetResult = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), 0, -1);
            if (CollectionUtil.isNotEmpty(packetSetResult)) {
                packetSetResult.stream().collect(Collectors.groupingBy(packet -> packet.getMessage().getFrom())).forEach((from, packetList)->{
                    if (CollectionUtil.isNotEmpty(packetList)) {
                        packetList.stream().collect(Collectors.groupingBy(Packet::getMessageType)).forEach((messageType, messageTypePackets) ->{
                            MessageEnum prototype = MessageEnum.prototype(messageType);
                            if (MessageEnum.IM_PRIVATE_CHAT.equals(prototype)) {
                                List<Packet<Message>> lastPackets = new ArrayList<>();
                                lastPackets.add(messageTypePackets.get(0));
                                unreadContentList.add(new UnreadContent(from, IMConstant.USER_TYPE_1, messageTypePackets.size(), lastPackets));
                            }
                            if (MessageEnum.IM_GROUP_CHAT.equals(prototype) && CollectionUtil.isNotEmpty(messageTypePackets)) {
                                messageTypePackets.stream().collect(Collectors.groupingBy(packet -> packet.getMessage().getTo())).forEach((to, groupMessagePackets)->{
                                    List<Packet<Message>> lastPackets = new ArrayList<>();
                                    lastPackets.add(groupMessagePackets.get(0));
                                    unreadContentList.add(new UnreadContent(to, IMConstant.GROUP_TYPE_2, groupMessagePackets.size(), lastPackets));
                                });
                            }
                        });
                    }
                });
                message.setContent(JSONUtil.toJsonStr(unreadContentList));
            }
            return result;
        }
        // 判断是按需来取还是全量拉取
        OfflineContent offlineContent = JSONUtil.toBean(message.getContent(), OfflineContent.class);
        List<Packet<Message>> packetList = offlineContent.getPacketList();
        // 如果传过来的消息id不为空，则可能是第N次拉取，从离线消息中删除消息
        if (CollectionUtil.isNotEmpty(packetList)) {
            for (Packet<Message> packet : packetList) {
                cacheOperator.removeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), packet);
            }
        }
        // 全量顺序拉取，一次拉取pullSize 大小的消息
        if (StrUtil.isNotBlank(offlineContent.getIdentity()) && (IMConstant.USER_TYPE_1.equals(offlineContent.getIdentityType()) || IMConstant.GROUP_TYPE_2.equals(offlineContent.getIdentityType()))) {
            // 按需拉取,先查出所有，然后过滤前几条给客户端
            Set<Packet<Message>> packetAllSet = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), 0, -1);
            if (CollectionUtil.isNotEmpty(packetAllSet)) {
                Iterator<Packet<Message>> iterator = packetAllSet.iterator();
                if (IMConstant.USER_TYPE_1.equals(offlineContent.getIdentityType())) {
                    while (iterator.hasNext()) {
                        Packet<Message> packet = iterator.next();
                        if (result.size() >= offlineContent.getPullSize()) {
                            break;
                        }
                        if (offlineContent.getIdentity().equals(packet.getMessage().getFrom()) && MessageEnum.IM_PRIVATE_CHAT.getValue() == packet.getMessageType()) {
                            result.add(packet);
                        }
                    }
                }
                if (IMConstant.GROUP_TYPE_2.equals(offlineContent.getIdentityType())) {
                    while (iterator.hasNext()) {
                        Packet<Message> packet = iterator.next();
                        if (result.size() >= offlineContent.getPullSize()) {
                            break;
                        }
                        if (offlineContent.getIdentity().equals(packet.getMessage().getTo()) && MessageEnum.IM_GROUP_CHAT.getValue() == packet.getMessageType()) {
                            result.add(packet);
                        }
                    }
                }
            }
        }else {
            // 全量分页拉取
            Set<Packet<Message>> packetSetResult = cacheOperator.reverseRangeZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + message.getFrom(), 0, offlineContent.getPullSize()-1);
            if (CollectionUtil.isNotEmpty(packetSetResult)) {
                Iterator<Packet<Message>> iterator = packetSetResult.iterator();
                while (iterator.hasNext()) {
                    result.add(iterator.next());
                }
            }
        }
        offlineContent.setPacketList(result);
        message.setContent(JSONUtil.toJsonStr(offlineContent));
        return result;
    }


    /**
     * 根据appKey 获取app 详情
     */
    public static ImAppDetail getAppDetail(String appKey) {
        ImAppDetail appDetail = (ImAppDetail) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.APP + appKey);
        if (appDetail == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            // 从数据库查询
            appDetail = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_IM_APP_DETAIL.sql(), ImAppDetail.class, appKey);
            if (appDetail != null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.APP + appKey, appDetail);
            }
        }
        return appDetail;
    }

    /**
     * 获取当前已经连im 连接数
     * @return
     */
    public static Integer getCurrentAppImConnections(String appKey) {
        Map<String, Object> currentImAppConnectionMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.APP + appKey + CacheConstant.CONNECTION );
        if (CollectionUtil.isNotEmpty(currentImAppConnectionMap)) {
            return currentImAppConnectionMap.size();
        }
        return IMConstant.ZERO;
    }

    /**
     * 绑定好友关系,只要一方删除好友，双方的联系人列表都会删除,所以只需获取一方是否是好友就可以
     * @param from
     * @param to
     */
    public static void bindFriend(String from, String to) {
        // 首先查询两个人是否是好友，如果不是好友则添加，如果是好友则不做处理
        ImFriendBO imFriendBO = (ImFriendBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to);
        // 已经是好友了
        if (imFriendBO != null) {
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + from);
        if (fromUser == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            fromUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, from);
            if (fromUser != null) {
                // 放到缓存中
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.IM_USER + from, fromUser);
            }
        }
        ImUser toUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + to);
        if (toUser == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            toUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, to);
            if (toUser != null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.IM_USER + to, toUser);
            }
        }
        // 绑定关系
        if (fromUser != null && toUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
                List<Object[]> argsList = new ArrayList<>();
                argsList.add(new Object[]{SnowflakeUtil.nextId(), fromUser.getId(), toUser.getId(), toUser.getNickName(), IMConstant.NOT_SHIELD, nowDateTime,nowDateTime});
                argsList.add(new Object[]{SnowflakeUtil.nextId(), toUser.getId(), fromUser.getId(), fromUser.getNickName(), IMConstant.NOT_SHIELD, nowDateTime,nowDateTime});
                dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_FRIEND.sql(),argsList);
            }
            // 添加到缓存，好友联系人
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + from, to, new ImFriendBO(fromUser.getId().toString(), toUser.getId().toString(), toUser.getNickName(), toUser.getUsername(), toUser.getEmail(), toUser.getPhoneNum(), toUser.getIdCardNum(), toUser.getAvatar(), toUser.getMotto(), toUser.getAge(), toUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.CONTACT + CacheConstant.FRIEND + to, from, new ImFriendBO(toUser.getId().toString(), fromUser.getId().toString(), fromUser.getNickName(), fromUser.getUsername(), fromUser.getEmail(), fromUser.getPhoneNum(), fromUser.getIdCardNum(), fromUser.getAvatar(), fromUser.getMotto(), fromUser.getAge(), fromUser.getSex(), IMConstant.NOT_SHIELD, nowDateTime, nowDateTime));
        }

    }

    /**
     * 加群/绑定群
     * @param from 客户端唯一标识
     * @param groupId  群唯一标识
     */
    public static void bindGroup(String from, String groupId) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
        // 已经存在群成员中
        if (imGroupUserBO != null) {
            return;
        }
        // 获取群信息
        ImGroup group = getGroup(groupId);
        if (group == null) {
            log.error("在绑定群关系中，获取群信息失败！");
            return;
        }
        // 从缓存获取用户信息
        ImUser fromUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + from);
        if (fromUser == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            fromUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, from);
            if (fromUser != null) {
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.IM_USER + from, fromUser);
            }
        }
        if (fromUser != null) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
                // 存入数据库
                dbOperator.insert(DbSqlConstant.MYSQL.INSERT_GROUP_USER.sql(), SnowflakeUtil.nextId(), groupId, from, group.getGroupName(), fromUser.getNickName(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, nowDateTime);
            }
            // 放入缓存
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from, new ImGroupUserBO(groupId, fromUser.getId().toString(), fromUser.getUsername(), fromUser.getNickName(), group.getGroupName(),fromUser.getEmail(),fromUser.getPhoneNum(),fromUser.getIdCardNum(),fromUser.getAvatar(),fromUser.getMotto(),fromUser.getAge(),fromUser.getSex(), IMConstant.NOT_GROUP_LEADER, IMConstant.NOT_GROUP_MANAGER, IMConstant.NOT_SHIELD, IMConstant.NOT_MUSHIN, nowDateTime));
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
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, to);
        }
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, to);
    }


    /**
     * 解散群
     * @param groupId
     */
    public static void disbandGroup(String groupId) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP.sql(), groupId);
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_ALL_USER.sql(), groupId);
        }
        cacheOperator.delete(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId);
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
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.delete(DbSqlConstant.MYSQL.DELETE_GROUP_USER.sql(), groupId, from);
        }
        cacheOperator.deleteHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
    }

    /**
     * 根据用户唯一标识获取，用户信息
     * @param identity
     * @return
     */
    public static ImUser getUser(String identity) {
        ImUser imUser = (ImUser) cacheOperator.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + identity);
        if (imUser == null && IMServerContext.SERVER_CONFIG.isDbEnable()){
            // 查询数据库
            imUser = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_USER.sql(), ImUser.class, identity);
            if (imUser != null) {
                // 放入缓存
                cacheOperator.put(CacheConstant.OUYUNC + CacheConstant.IM_USER + identity, imUser);
            }
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
        if (imGroup == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
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
        if (imFriendBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imFriendBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_FRIEND_USER.sql(), ImFriendBO.class, from, to);
        }
        return imFriendBO;
    }


    /**
     * 获取from在to中的用户信息,该用户在群中的信息
     * @param from 发送者
     * @param groupId 群唯一标识
     * @return
     */
    public static ImGroupUserBO getGroupMember(String from, String groupId) {
        ImGroupUserBO imGroupUserBO = (ImGroupUserBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS, from);
        if (imGroupUserBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imGroupUserBO = dbOperator.selectOne(DbSqlConstant.MYSQL.SELECT_GROUP_USER.sql(), ImGroupUserBO.class, from, groupId);
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
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            imUserList = dbOperator.batchSelect(isGroupManager ? DbSqlConstant.MYSQL.SELECT_GROUP_LEADER_USERS.sql():DbSqlConstant.MYSQL.SELECT_GROUP_USERS.sql(), ImGroupUserBO.class, to);
            if (CollectionUtil.isNotEmpty(imUserList)) {
                Map<Object, ImGroupUserBO> groupUserMap = new HashMap<>();
                imUserList.forEach(groupUser ->{
                    groupUserMap.put(groupUser.getUserId(), groupUser);
                });
                cacheOperator.putHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + to + CacheConstant.MEMBERS, groupUserMap);
            }
        }
        return imUserList;
    }

    /**
     * 根据群组id，返回群组中，当前所有成员
     * @param groupId 群组唯一标识
     * @return
     */
    public static List<ImGroupUserBO> getGroupMembers(String groupId) {
        return getGroupMembers(groupId, false);
    }

    /**
     * @Author fangzhenxun
     * @Description 获取该群的群主信息
     * @param groupId
     * @return com.ouyunc.im.domain.bo.ImGroupUserBO
     */
    public static ImGroupUserBO getGroupLeader(String groupId) {
        Map<String, Object> groupUserBOMap = cacheOperator.getHashAll(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.GROUP + groupId + CacheConstant.MEMBERS);
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
     * @param from
     * @param to
     * @param type 1-用户的黑名单，2-群组的黑名单
     * @return
     */
    public static ImBlacklistBO getBackList(String from, String to, Integer type) {
        if (IMConstant.USER_TYPE_1.equals(type)) {
            ImBlacklistBO imBlacklistBO = (ImBlacklistBO) cacheOperator.getHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.BLACK_LIST +  CacheConstant.USER + to, from);
            if (imBlacklistBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
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
            if (imBlacklistBO == null && IMServerContext.SERVER_CONFIG.isDbEnable()) {
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
     * 处理消息已读回执，开线程处理
     * @param from 发送者唯一标识
     * @param readReceiptList
     * @return
     */
    public static void writeMessageReadReceipt(String from, List<ReadReceiptContent> readReceiptList) {
        if (CollectionUtil.isEmpty(readReceiptList)) {
            return;
        }
        ImUser user = getUser(from);
        List<Object[]> batchArgs = new ArrayList<>();
        for (ReadReceiptContent readReceiptContent : readReceiptList) {
            batchArgs.add(new Object[] {SnowflakeUtil.nextId(), readReceiptContent.getPacketId(), from});
            cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.READ_RECEIPT + readReceiptContent.getPacketId(), from, user);
        }
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            dbOperator.batchInsert(DbSqlConstant.MYSQL.INSERT_READ_RECEIPT.sql(), batchArgs);
        }
    }

    /**
     * 添加原始消息到数据库
     * @param packet
     */
    public static void writeMessage(Packet packet) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            Message message = (Message) packet.getMessage();
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), JSONUtil.toJsonStr(message.getContent()), message.getCreateTime(), nowDateTime, nowDateTime);
        }
    }

    /**
     * @Author fangzhenxun
     * @Description 将消息写到from的 发件箱
     * @param packet
     * @param from
     * @return void
     */
    public static void write2SendTimeline(Packet packet, String from, long timestamp) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Message message = (Message) packet.getMessage();
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_SEND_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), message.getContent(), message.getCreateTime(), nowDateTime, nowDateTime);
        }
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.SEND + from, packet, timestamp);
    }

    /**
     * @Author fangzhenxun
     * @Description 将消息写到to 的收件箱
     * @param packet
     * @param to
     * @return void
     */
    public static void write2ReceiveTimeline(Packet packet, String to, long timestamp) {
        if (IMServerContext.SERVER_CONFIG.isDbEnable()) {
            String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            Message message = (Message) packet.getMessage();
            dbOperator.insert(DbSqlConstant.MYSQL.INSERT_RECEIVE_MESSAGE.sql(), packet.getPacketId(), packet.getProtocol(), packet.getProtocolVersion(), packet.getDeviceType(), packet.getNetworkType(), packet.getEncryptType(), packet.getSerializeAlgorithm(), packet.getIp(), message.getFrom(), message.getTo(), packet.getMessageType(), message.getContentType(), message.getContent(), message.getCreateTime(), nowDateTime, nowDateTime);
        }
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.RECEIVE + to, packet, timestamp);
    }

    /**
     * 存入离线消息
     * @param identity 消息发送者
     * @param packet
     */
    public static void write2OfflineTimeline(Packet packet, String identity, long timestamp) {
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.OFFLINE + identity, packet, timestamp);
    }

    /**
     * 将from加入to的黑名单
     * @param from
     * @param to
     * @param type  1-用户的黑名单，2-群组的黑名单
     */
    public static void joinBlacklist(String from, String to, Integer type) {
        String nowDateTime = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
        ImUser fromUser = getUser(from);
        if (fromUser == null) {
            log.error("在加入黑名单处理中，获取用户失败！");
            return;
        }
        ImBlacklistBO imBlacklistBO = new ImBlacklistBO(to, type, from, fromUser.getUsername(), fromUser.getNickName(),fromUser.getEmail(), fromUser.getPhoneNum(), fromUser.getIdCardNum(), fromUser.getAvatar(), fromUser.getMotto(), fromUser.getAge(), fromUser.getSex(), nowDateTime);
        String suffix = CacheConstant.USER;
        if (IMConstant.GROUP_TYPE_2.equals(type)) {
            suffix = CacheConstant.GROUP;
        }
        cacheOperator.putHash(CacheConstant.OUYUNC + CacheConstant.IM + CacheConstant.BLACK_LIST +  suffix + to, from, imBlacklistBO);
        dbOperator.insert(DbSqlConstant.MYSQL.INSERT_BLACK_LIST.sql(), SnowflakeUtil.nextId(), to, from, type, nowDateTime);
    }

    /**
     * 处理好友请求相关逻辑
     * @param packet
     */
    public static void handleFriendRequest(Packet packet) {
        Message message = (Message) packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        MessageContentEnum messageContentEnum = MessageContentEnum.prototype(message.getContentType());
        // 添加好友请求， 拒绝好友请求直接转发消息，只有同意好友请求才会绑定好友关系
        // 如果是好友申请，直接转发给对方各个端，不做消息保存；如果A和B同时添加好友，同时同意，则只会保留一份关系
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.GROUP + CacheConstant.REFUSE_AGREE + IdentityUtil.sortComboIdentity(from, to));
        try{
            lock.lock();
            if (MessageContentEnum.FRIEND_AGREE.equals(messageContentEnum)) {
                bindFriend(from, from);
            }
            if (MessageContentEnum.FRIEND_REFUSE_AND_JOIN_BLACKLIST.equals(messageContentEnum)) {
                // 加入黑名单
                joinBlacklist(to, from, IMConstant.USER_TYPE_1);
            }
            long timestamp = SystemClock.now();
            cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.FRIEND_REQUEST + from, packet, timestamp);
            cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.FRIEND_REQUEST + to, packet, timestamp);
        }finally {
            lock.unlock();
        }
    }

    /**
     * 处理群组请求
     * @param packet
     */
    public static void handleGroupRequest(Packet packet) {
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        String groupId = groupRequestContent.getGroupId();
        String identity = groupRequestContent.getIdentity();
        MessageContentEnum messageContentEnum = MessageContentEnum.prototype(message.getContentType());
        if (MessageContentEnum.GROUP_REFUSE_AND_JOIN_BLACKLIST.equals(messageContentEnum)) {
            joinBlacklist(identity, groupId, IMConstant.GROUP_TYPE_2);
        }
        long timestamp = SystemClock.now();
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.GROUP_REQUEST + identity, packet, timestamp);
        cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.GROUP_REQUEST + groupId, packet, timestamp);
    }
}
