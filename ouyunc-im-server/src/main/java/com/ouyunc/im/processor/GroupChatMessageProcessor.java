package com.ouyunc.im.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.InnerExtraData;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.SystemClock;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 群聊消息处理器
 */
public class GroupChatMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(GroupChatMessageProcessor.class);

    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_GROUP_CHAT;
    }


    /**
     * @param ctx
     * @param packet
     * @return void
     * @Author fangzhenxun
     * @Description 群聊做权限的过滤
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
        InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
        String appKey = innerExtraData.getAppKey();
        EVENT_EXECUTORS.execute(() -> DbHelper.writeMessage(appKey, packet));
        // 消息发送方
        String from = message.getFrom();
        // 消息接收方
        String to = message.getTo();
        // ===================================做校验(@todo 这里可以改造，各种校验不通过后响应相关消息给客户端)=========================================
        if (!MessageValidate.isAuth(appKey, from, packet.getDeviceType(), ctx) || MessageValidate.isBanned(appKey, from, IMConstant.USER_TYPE_1) || MessageValidate.isBanned(appKey, to, IMConstant.GROUP_TYPE_2) || !MessageValidate.isGroup(appKey, from, to) || MessageValidate.isBackList(appKey, from, to, IMConstant.GROUP_TYPE_2)) {
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }

    /**
     * 群组逻辑处理
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupChatMessageProcessor 正在处理群聊消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0) -> {
            Message message = (Message) packet.getMessage();
            ExtraMessage extraMessage = JSON.parseObject(message.getExtra(), ExtraMessage.class);
            InnerExtraData innerExtraData = extraMessage.getInnerExtraData();
            String appKey = innerExtraData.getAppKey();

            // from 代表群组中的发送者
            String from = message.getFrom();
            // to 代表群组唯一表示
            String to = message.getTo();
            // 根据群唯一标识to,获取当前群中所有群成员
            // 首先从缓存中获取群成员(包括自身)，如果没有在从数据库获取
            List<ImGroupUserBO> groupMembers = DbHelper.getGroupMembers(appKey, to);
            // 循环遍历
            if (CollectionUtils.isEmpty(groupMembers)) {
                // 解散了 (该群不存在了)
                return;
            }
            // 写入发件箱
            long timestamp = SystemClock.now();
            DbHelper.write2Timeline(appKey, packet, from, to, timestamp);
            // 给自己的所有登录端去发送信息
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(appKey, from, packet.getDeviceType());
            // 转发给自己客户端的各个设备端，排除自己
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            for (ImGroupUserBO groupMember : groupMembers) {
                // 目前使用id号来作为唯一标识
                if (!from.equals(groupMember.getUserId()) && IMConstant.NOT_SHIELD.equals(groupMember.getIsShield())) {
                    // 无论是否在线都会先存入离线消息表
                    DbHelper.write2OfflineTimeline(appKey, packet, groupMember.getUserId(), timestamp);
                    // 判断，群成员是否屏蔽了该群，如果屏蔽则不能接受到该消息
                    List<LoginUserInfo> othersMembersLoginUserInfos = UserHelper.onlineAll(appKey, groupMember.getUserId());
                    if (CollectionUtils.isNotEmpty(othersMembersLoginUserInfos)) {
                        MessageHelper.send2MultiDevices(packet, othersMembersLoginUserInfos);
                    }
                }

            }
        });

    }
}
