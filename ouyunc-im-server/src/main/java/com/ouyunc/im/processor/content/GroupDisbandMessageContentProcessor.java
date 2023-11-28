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
 * 解散群
 */
public class GroupDisbandMessageContentProcessor extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupDisbandMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_DISBAND;
    }

    /**
     * @Author fangzhenxun
     * @Description 群主解散群
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupDisbandMessageContentProcessor 正在处理群拒绝请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSON.parseObject(message.getContent(), GroupRequestContent.class);
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        ImGroupUserBO groupMember = DbHelper.getGroupMember(from, groupRequestContent.getGroupId());
        // 群主只能解散和转让群主
        if (!IMConstant.GROUP_LEADER.equals(groupMember.getIsLeader())) {
            return;
        }
        // 解散群
        List<ImGroupUserBO> groupMembers = DbHelper.getGroupMembers(groupRequestContent.getGroupId());
        DbHelper.disbandGroup(groupRequestContent.getGroupId());
        if (CollectionUtils.isEmpty(groupMembers)) {
            return;
        }
        for (ImGroupUserBO member : groupMembers) {
            // 排除群主自身
            if (!from.equals(member.getUserId())) {
                DbHelper.write2OfflineTimeline(packet,member.getUserId(), SystemClock.now());
                List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(member.getUserId());
                if (CollectionUtils.isNotEmpty(managersLoginUserInfos)) {
                    MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
                }
            }
        }

    }
}
