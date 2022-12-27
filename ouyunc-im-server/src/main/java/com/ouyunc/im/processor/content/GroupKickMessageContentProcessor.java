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
 * 踢出群群
 */
public class GroupKickMessageContentProcessor extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupKickMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_KICK;
    }

    /**
     * @Author fangzhenxun
     * @Description 踢出群
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupKickMessageContentProcessor 正在处理踢出群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);

        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        // 判断from 发起者是否有事群主或管理员
        ImGroupUserBO groupMember = DbHelper.getGroupMember(from, groupRequestContent.getGroupId());
        if (!IMConstant.GROUP_MANAGER.equals(groupMember.getIsLeader()) && !IMConstant.GROUP_LEADER.equals(groupMember.getIsLeader())) {
            return;
        }
        // 根据to从分布式缓存中取出targetServerAddress目标地址
        String to = message.getTo();
        DbHelper.removeOutGroup(to, groupRequestContent.getGroupId());
        // 判断该管理员是否在线，如果不在线放入离线消息
        List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
        if (CollectionUtil.isEmpty(toLoginUserInfos)) {
            // 存入离线消息
            DbHelper.write2OfflineTimeline(packet, to);
            return;
        }
        MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
    }
}
