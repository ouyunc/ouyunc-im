package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.domain.ImGroupUser;
import com.ouyunc.im.domain.ImUser;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import io.netty.channel.ChannelHandlerContext;
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
     * 群组逻辑处理
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupChatMessageProcessor 正在处理群聊消息packet: {}", packet);
        fireProcess(ctx, packet, (ctx0, packet0)->{
            Message message = (Message) packet.getMessage();
            ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
            if (extraMessage == null) {
                extraMessage = new ExtraMessage();
            }
            // from 代表群组中的发送者
            String from = message.getFrom();
            // to 代表群组唯一表示
            String to = message.getTo();
            // 判断是否从其他服务路由过来的额消息
            if (extraMessage.isDelivery()) {
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(extraMessage.getTargetServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, extraMessage.getDeviceEnum().getName()));
                    return;
                }
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(extraMessage.getTargetServerAddress()));
                return;
            }
            // 根据群唯一标识to,获取当前群中所有群成员
            // 首先从缓存中获取群成员(包括自身)，如果没有在从数据库获取
            List<ImGroupUser> groupMembers = DbHelper.getGroupMembers(to);
            // 循环遍历
            if (CollectionUtil.isEmpty(groupMembers)) {
                // 解散了
                return;
            }
            // 遍历所有的群成员
            for (ImGroupUser groupMember : groupMembers) {
                // 目前使用id号来作为唯一标识
                if (from.equals(groupMember.getUserId())) {
                    // 如果是自己找到自己的所有登录端去发送信息
                    List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from);
                    // 排除自己，发给其他端
                    fromLoginUserInfos.forEach(fromLoginUserInfo -> {
                        // 排除自己发给其他端
                        if (!IdentityUtil.generalComboIdentity(from, packet.getDeviceType()).equals(IdentityUtil.generalComboIdentity(fromLoginUserInfo.getIdentity(), fromLoginUserInfo.getDeviceEnum().getName()))) {
                            // 走消息传递,设置登录设备类型
                            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(fromLoginUserInfo.getLoginServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                                MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(from, fromLoginUserInfo.getDeviceEnum().getName()));
                            } else {
                                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(fromLoginUserInfo.getLoginServerAddress()));
                            }
                        }
                    });
                } else {
                    // 群里其它人员的其他端
                    List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(groupMember.getUserId().toString());
                    if (CollectionUtil.isEmpty(toLoginUserInfos)) {
                        // 存入离线消息
                        DbHelper.addOfflineMessage(to, packet);
                        return;
                    }
                    // 转发给某个客户端的各个设备端
                    for (LoginUserInfo loginUserInfo : toLoginUserInfos) {
                        // 走消息传递,设置登录设备类型
                        if (!IMServerContext.SERVER_CONFIG.isClusterEnable() || IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(loginUserInfo.getLoginServerAddress())) {
                            MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(from, loginUserInfo.getDeviceEnum().getName()));
                        } else {
                            MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(loginUserInfo.getLoginServerAddress()));
                        }
                    }
                }
            }
        });

    }
}
