package com.ouyunc.im.processor.content;


import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.utils.SystemClock;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 退群
 */
public class GroupExitMessageContentProcessor extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupExitMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_EXIT;
    }

    /**
     * @Author fangzhenxun
     * @Description 群成员退群通知
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupExitMessageContentProcessor 正在处理退群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSON.parseObject(message.getContent(), GroupRequestContent.class);
        String from = message.getFrom();
        ImGroupUserBO groupMember = DbHelper.getGroupMember(from, groupRequestContent.getGroupId());
        // 群主只能解散和转让群主
        if (IMConstant.GROUP_LEADER.equals(groupMember.getIsLeader())) {
            return;
        }
        // 退出群
        DbHelper.exitGroup(from, groupRequestContent.getGroupId());
        // 查找群中的群主
        ImGroupUserBO groupLeader = DbHelper.getGroupLeader(groupRequestContent.getGroupId());
        DbHelper.write2OfflineTimeline(packet, groupLeader.getUserId(), SystemClock.now());
        // 判断群主是否在线，如果不在线放入离线消息
        List<LoginUserInfo> leaderLoginUserInfos = UserHelper.onlineAll(groupLeader.getUserId());
        if (CollectionUtils.isNotEmpty(leaderLoginUserInfos)) {
            MessageHelper.send2MultiDevices(packet, leaderLoginUserInfos);
        }
    }
}
