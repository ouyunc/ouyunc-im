package com.ouyunc.im.processor;

import com.alibaba.fastjson2.JSON;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageTypeEnum;
import com.ouyunc.im.context.IMProcessContext;
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
 * 客服消息处理
 */
public class CustomerMessageProcessor extends AbstractMessageProcessor {
    private static Logger log = LoggerFactory.getLogger(CustomerMessageProcessor.class);


    @Override
    public MessageTypeEnum messageType() {
        return MessageTypeEnum.IM_CUSTOMER;
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
        // ===================================做校验================================
        if (!MessageValidate.isAuth(appKey, from, packet.getDeviceType(), ctx) || MessageValidate.isBanned(appKey, from, IMConstant.USER_TYPE_1) || MessageValidate.isBanned(appKey, to, IMConstant.GROUP_TYPE_2) || !MessageValidate.isGroup(appKey, from, to) || MessageValidate.isBackList(appKey, from, to, IMConstant.GROUP_TYPE_2)) {
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }

    /**
     * 真正处理客服信息
     *
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("CustomerMessageProcessor 正在处理客服消息packet: {}", packet);
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
                // 该客户没有分配客服，不予处理
                return;
            }
            // 写入发件箱
            long timestamp = SystemClock.now();
            DbHelper.write2Timeline(appKey, packet, from, to, timestamp);
            // 如果是自己找到自己的所有登录端去发送信息
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(appKey, from, packet.getDeviceType());
            // 排除自己，发给其他端
            // 转发给自己客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            // 遍历所有的客服人员，一般一个群里只会有一个客服（默认为机器人客服，如果该机器人账号登录了就是真人客服）和一个客户
            // 定义登录计数器，如果该群里都没有人（人工登录）则交给机器人处理
            int groupLoginUserCount = 1;
            for (ImGroupUserBO groupMember : groupMembers) {
                // 目前使用id号来作为唯一标识
                if (!from.equals(groupMember.getUserId()) && IMConstant.NOT_SHIELD.equals(groupMember.getIsShield())) {
                    // 无论是否在线都会先存入离线消息表
                    DbHelper.write2OfflineTimeline(appKey, packet, groupMember.getUserId(), timestamp);
                    // 判断，客服是否屏蔽了该群，如果屏蔽则不能接受到该消息
                    List<LoginUserInfo> customerLoginUserInfos = UserHelper.onlineAll(appKey, groupMember.getUserId());
                    if (CollectionUtils.isEmpty(customerLoginUserInfos)) {
                        // 如果群里非机器人都没有登录，并且群里只有机器人和客户两个人，则有机器人接管发送
                        groupLoginUserCount++;
                    } else {
                        // 如果登录了，则是人工客服介入了
                        MessageHelper.send2MultiDevices(packet, customerLoginUserInfos);
                    }
                }
            }
            // 判断是否交给机器人处理客服消息，在该群组中只要有一个登录了就不会托管给机器人处理
            if (groupLoginUserCount == groupMembers.size()) {
                EVENT_EXECUTORS.execute(() -> IMProcessContext.CHAT_BOT_PROCESSOR.get(0).doProcess(ctx, packet));
            }
        });

    }

}
