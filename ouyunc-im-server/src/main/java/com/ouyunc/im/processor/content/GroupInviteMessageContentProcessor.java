package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.ImUser;
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
import java.util.Optional;

/**
 * 群成员邀请他人加入群
 */
public class GroupInviteMessageContentProcessor  extends AbstractMessageContentProcessor{
    private static Logger log = LoggerFactory.getLogger(GroupInviteMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_INVITE_JOIN;
    }

    /**
     * 处理群成员邀请他人加入该群（有可能被邀请人已经是该群成员，做最大兼容性）
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupInviteMessageContentProcessor 正在处理邀请加群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        // 被邀请人id 集合
        JSONArray invitedUserIds = JSONUtil.parseArray(groupRequestContent.getData());
        // 判断邀请加入的好友是否为空
        if (CollectionUtil.isNotEmpty(invitedUserIds)) {
            // 处理群请求
            DbHelper.handleGroupInviteRequest(packet);
            for (Object invitedUserId : invitedUserIds) {
                // 判断该用户是否在线，如果不在线放入离线消息
                List<LoginUserInfo> invitedLoginUserInfos = UserHelper.onlineAll((String) invitedUserId);
                if (CollectionUtil.isEmpty(invitedLoginUserInfos)) {
                    // 存入离线消息
                    DbHelper.write2OfflineTimeline(packet, (String) invitedUserId, SystemClock.now());
                }else {
                    // 转发给某个客户端的各个设备端
                    MessageHelper.send2MultiDevices(packet, invitedLoginUserInfos);
                }

            }

        }
    }
}
