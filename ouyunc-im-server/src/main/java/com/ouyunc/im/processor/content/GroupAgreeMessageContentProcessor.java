package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.lock.DistributedLock;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 同意加群
 */
public class GroupAgreeMessageContentProcessor extends AbstractMessageContentProcessor{

    private static Logger log = LoggerFactory.getLogger(GroupAgreeMessageContentProcessor.class);

    @Override
    public MessageContentEnum messageContentType() {
        return MessageContentEnum.GROUP_AGREE;
    }


    @DistributedLock(lockName = CacheConstant.OUYUNC + CacheConstant.LOCK + CacheConstant.GROUP + CacheConstant.REFUSE_AGREE)
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupRefuseMessageContentProcessor 正在处理群拒绝请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        GroupRequestContent groupRequestContent = JSONUtil.toBean(message.getContent(), GroupRequestContent.class);
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        // 根据to从分布式缓存中取出targetServerAddress目标地址
        String to = message.getTo();
        // 检查是否已经处理该条消息，如果处理了则不做消息的转发
        // 判断是否已经是好友
        if (MessageValidate.isGroup(to, groupRequestContent.getGroupId())) {
            return;
        }
        // 绑定群成员关系
        DbHelper.joinGroup(from, to);

        // 查找群中的管理员以及群主，向其投递加群的请求
        List<ImGroupUserBO> groupManagerMembers = DbHelper.getGroupMembers(groupRequestContent.getGroupId(), true);
        if (CollectionUtil.isEmpty(groupManagerMembers)) {
            return;
        }
        for (ImGroupUserBO groupManagerMember : groupManagerMembers) {
            // 排除自己
            if (!from.equals(groupManagerMember.getUserId())) {
                // 判断该管理员是否在线，如果不在线放入离线消息
                List<LoginUserInfo> managersLoginUserInfos = UserHelper.onlineAll(groupManagerMember.getUserId().toString());
                if (CollectionUtil.isEmpty(managersLoginUserInfos)) {
                    // 存入离线消息
                    DbHelper.write2OfflineTimeline(packet, groupManagerMember.getUserId().toString());
                    return;
                }
                // 转发给某个客户端的各个设备端
                MessageHelper.send2MultiDevices(packet, managersLoginUserInfos);
            }
        }

        // 判断该对方是否在线，如果不在线放入离线消息，注意该消息不存离线，如果用户不在线则丢弃该消息
        List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
        // 转发给某个客户端的各个设备端
        MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
    }
}
