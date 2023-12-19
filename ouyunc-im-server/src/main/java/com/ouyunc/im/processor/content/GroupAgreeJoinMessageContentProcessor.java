package com.ouyunc.im.processor.content;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.cache.l1.distributed.redis.redisson.RedissonFactory;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SystemClock;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.redisson.api.RLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 同意加群
 */
public class GroupAgreeJoinMessageContentProcessor extends AbstractMessageContentProcessor {


    private static Logger log = LoggerFactory.getLogger(GroupAgreeJoinMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_AGREE;
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupAgreeJoinMessageContentProcessor 正在处理群拒绝请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        String appKey = innerExtraData.getAppKey();
        GroupRequestContent groupRequestContent = JSON.parseObject(message.getContent(), GroupRequestContent.class);
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        String groupId = groupRequestContent.getGroupId();
        // 被邀请人id
        List<String> invitedUserIdList = groupRequestContent.getInvitedUserIdList();
        String identity = groupRequestContent.getIdentity();
        // 如果被邀请人不为空，则是邀请的同意处理
        if (CollectionUtils.isNotEmpty(invitedUserIdList)) {
            identity = invitedUserIdList.get(0);
        }
        //获取锁
        RLock lock = RedissonFactory.INSTANCE.redissonClient().getLock(CacheConstant.OUYUNC + CacheConstant.APP_KEY + appKey + CacheConstant.COLON + CacheConstant.LOCK + CacheConstant.GROUP + CacheConstant.REFUSE_AGREE + IdentityUtil.sessionId(identity, groupId));
        long timestamp;
        try {
            lock.lock();
            // 检查是否已经处理该条消息，如果处理了则不做消息的转发
            // 判断是否已经是好友
            if (MessageValidate.isGroup(appKey, identity, groupId)) {
                return;
            }
            // 处理群组请求
            timestamp = SystemClock.now();
            DbHelper.bindGroup(appKey, identity, groupId);
            DbHelper.handleGroupRequestMessage(appKey, packet, identity, timestamp);
            DbHelper.handleGroupRequestMessage(appKey, packet, groupId, timestamp);
        } finally {
            lock.unlock();
        }
        // 查找群中的管理员以及群主，向其投递加群的请求
        List<ImGroupUserBO> groupManagerMembers = DbHelper.getGroupMembers(appKey, groupId, true);
        if (CollectionUtils.isEmpty(groupManagerMembers)) {
            return;
        }
        for (ImGroupUserBO groupManagerMember : groupManagerMembers) {
            // 排除自己
            if (!from.equals(groupManagerMember.getUserId())) {
                // 无论是否在线都会先存入离线消息中
                DbHelper.write2OfflineTimeline(appKey, packet, groupManagerMember.getUserId(), timestamp);
                // 判断该管理员是否在线，如果不在线放入离线消息
                List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(appKey, groupManagerMember.getUserId());
                if (CollectionUtils.isNotEmpty(managersLoginUserInfos)) {
                    MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
                }
            }
        }
        // 发消息给申请请人或被邀请人
        DbHelper.write2OfflineTimeline(appKey, packet, identity, timestamp);
        List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(appKey, identity);
        // 转发给某个客户端的各个设备端
        if (CollectionUtils.isNotEmpty(toLoginUserInfos)) {
            MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
        }
    }
}
