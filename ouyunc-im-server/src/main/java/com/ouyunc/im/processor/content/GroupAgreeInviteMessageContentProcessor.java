package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 被邀请者同意邀请加入
 */
public class GroupAgreeInviteMessageContentProcessor extends AbstractMessageContentProcessor{


    private static Logger log = LoggerFactory.getLogger(GroupAgreeInviteMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_INVITE_AGREE;
    }


    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupAgreeInviteMessageContentProcessor 正在处理群邀请同意请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        String from = message.getFrom();
        String to = message.getTo();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        // 群组id
        String groupId = groupRequestContent.getGroupId();
        // 获取当前邀请人在群中的身份（群主、管理员, 普通成员），如果邀请人都不存在了群组中，就直接不作为处理
        ImGroupUserBO visitor = DbHelper.getGroupMember(to, groupId);
        if (visitor == null) {
            return;
        }
        long now = SystemClock.now();
        if (IMConstant.GROUP_LEADER.equals(visitor.getIsLeader()) || IMConstant.GROUP_MANAGER.equals(visitor.getIsManager()) ) {
            DbHelper.bindGroup(from, groupId);
            // 这里使用了一个额外字段来处理邀请状态的流转
            groupRequestContent.setExtra(IMConstant.GROUP_LEADER_OR_MANAGER);
            message.setContent(JSONUtil.toJsonStr(groupRequestContent));
        }else {
            // 该请求是普通成员的邀请，需要发送给该群的其他群主或管理员去授权同意
            // 查找群中的管理员以及群主，向其投递加群的请求
            List<ImGroupUserBO> groupManagerMembers = DbHelper.getGroupMembers(groupRequestContent.getGroupId(), true);
            if (CollectionUtil.isEmpty(groupManagerMembers)) {
                return;
            }
            for (ImGroupUserBO groupManagerMember : groupManagerMembers) {
                // 判断该管理员是否在线，如果不在线放入离线消息
                List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(groupManagerMember.getUserId());
                if (CollectionUtil.isEmpty(managersLoginUserInfos)) {
                    // 存入离线消息
                    DbHelper.write2OfflineTimeline(packet, groupManagerMember.getUserId(), now);
                }else {
                    // 转发给某个客户端的各个设备端
                    MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
                }
            }
            // 只缓存到群请求消息中
            DbHelper.cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.GROUP_REQUEST + groupId, packet, now);
        }
        DbHelper.cacheOperator.addZset(CacheConstant.OUYUNC + CacheConstant.IM_MESSAGE + CacheConstant.GROUP_REQUEST + from, packet, now);
        // 对群邀请人来讲并不太关心被邀请人同意不同意，所以这里就不进行消息通知邀请人了，和邀请的时候一致，也不保存邀请的信息
    }
}
