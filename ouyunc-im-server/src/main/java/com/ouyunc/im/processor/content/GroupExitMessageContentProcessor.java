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
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
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
        // 判断该管理员是否在线，如果不在线放入离线消息
        List<LoginUserInfo> leaderLoginUserInfos = UserHelper.onlineAll(groupLeader.getUserId().toString());
        if (CollectionUtil.isEmpty(leaderLoginUserInfos)) {
            // 存入离线消息
            DbHelper.write2OfflineTimeline(packet, groupLeader.getUserId().toString());
            return;
        }
        // 转发给某个客户端的各个设备端
        MessageHelper.send2MultiDevices(packet, leaderLoginUserInfos);
    }
}
