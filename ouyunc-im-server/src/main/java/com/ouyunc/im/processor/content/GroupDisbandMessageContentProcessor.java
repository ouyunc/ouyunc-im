package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
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
import io.netty.channel.ChannelHandlerContext;
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
        log.info("GroupRefuseMessageContentProcessor 正在处理群拒绝请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        ImGroupUserBO groupMember = DbHelper.getGroupMember(from, groupRequestContent.getGroupId());
        // 群主只能解散和转让群主
        if (!IMConstant.GROUP_LEADER.equals(groupMember.getIsLeader())) {
            return;
        }
        // 解散群
        DbHelper.disbandGroup(groupRequestContent.getGroupId());
        List<ImGroupUserBO> groupMembers = DbHelper.getGroupMembers(groupRequestContent.getGroupId());
        if (CollectionUtil.isEmpty(groupMembers)) {
            return;
        }
        for (ImGroupUserBO member : groupMembers) {
            // 排除群主自身
            if (!from.equals(member.getUserId())) {
                // 判断该管理员是否在线，如果不在线放入离线消息
                List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(member.getUserId().toString());
                if (CollectionUtil.isEmpty(managersLoginUserInfos)) {
                    // 存入离线消息
                    DbHelper.addOfflineMessage(member.getUserId().toString(), packet);
                    return;
                }
                // 转发给某个客户端的各个设备端
                MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
            }
        }

    }
}
