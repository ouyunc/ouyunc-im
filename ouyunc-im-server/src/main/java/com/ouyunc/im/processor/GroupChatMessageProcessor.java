package com.ouyunc.im.processor;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.domain.bo.ImGroupUserBO;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
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
     * @Author fangzhenxun
     * @Description 群聊做权限的过滤
     * @param ctx
     * @param packet
     * @return void
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        EVENT_EXECUTORS.execute(() -> DbHelper.writeMessage(packet));
        Message message = (Message) packet.getMessage();
        // 消息发送方
        String from = message.getFrom();
        // 消息接收方
        String to = message.getTo();
        // ===================================做校验(@todo 这里可以改造，各种校验不通过后响应相关消息给客户端)=========================================
        if (!MessageValidate.isAuth(from, packet.getDeviceType(), ctx) || MessageValidate.isBanned(from, IMConstant.USER_TYPE_1) || MessageValidate.isBanned(to, IMConstant.GROUP_TYPE_2) || !MessageValidate.isGroup(from, to) || MessageValidate.isBackList(from, to, IMConstant.GROUP_TYPE_2)) {
            return;
        }
        // 交给下个处理
        ctx.fireChannelRead(packet);
    }

    /**
     * 群组逻辑处理
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupChatMessageProcessor 正在处理群聊消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            // from 代表群组中的发送者
            String from = message.getFrom();
            // to 代表群组唯一表示
            String to = message.getTo();
            // 根据群唯一标识to,获取当前群中所有群成员
            // 首先从缓存中获取群成员(包括自身)，如果没有在从数据库获取
            List<ImGroupUserBO> groupMembers = DbHelper.getGroupMembers(to);
            // 循环遍历
            if (CollectionUtils.isEmpty(groupMembers)) {
                // 解散了
                return;
            }
            // 写入发件箱
            long timestamp = SystemClock.now();
            DbHelper.write2SendTimeline(packet, from, timestamp);
            // 将消息存一份到群消息中
            DbHelper.write2ReceiveTimeline(packet, to, timestamp);
            // 遍历所有的群成员
            for (ImGroupUserBO groupMember : groupMembers) {
                // 目前使用id号来作为唯一标识
                if (from.equals(groupMember.getUserId())) {
                    // 如果是自己找到自己的所有登录端去发送信息
                    List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from, packet.getDeviceType());
                    // 排除自己，发给其他端
                    // 转发给自己客户端的各个设备端
                    MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
                } else {
                    // 群里其它人员的其他端
                    // 判断，群成员是否屏蔽了该群，如果屏蔽则不能接受到该消息
                    if (IMConstant.NOT_SHIELD.equals(groupMember.getIsShield())) {
                        List<LoginUserInfo> othersMembersLoginUserInfos = UserHelper.onlineAll(groupMember.getUserId());
                        if (CollectionUtils.isEmpty(othersMembersLoginUserInfos)) {
                            // 存入离线消息
                            DbHelper.write2OfflineTimeline(packet, groupMember.getUserId(), timestamp);
                        }else {
                            // 转发给某个客户端的各个设备端
                            MessageHelper.send2MultiDevices(packet, othersMembersLoginUserInfos);
                        }
                    }
                }
            }
        });

    }
}
