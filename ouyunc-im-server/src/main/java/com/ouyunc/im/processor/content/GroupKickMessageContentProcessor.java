package com.ouyunc.im.processor.content;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageContentEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.GroupRequestContent;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
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

    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("GroupKickMessageContentProcessor 正在处理踢出群请求 packet: {}...", packet);
        Message message = (Message) packet.getMessage();
        ExtraMessage extraMessage = JSONUtil.toBean(message.getExtra(), ExtraMessage.class);
        if (extraMessage == null) {
            extraMessage = new ExtraMessage();
        }
        // 下面是对集群以及qos消息可靠进行处理
        String from = message.getFrom();
        // 根据to从分布式缓存中取出targetServerAddress目标地址
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

        // 判断该管理员是否在线，如果不在线放入离线消息
        List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
        if (CollectionUtil.isEmpty(toLoginUserInfos)) {
            // 存入离线消息
            DbHelper.addOfflineMessage(to, packet);
            return;
        }
        // 转发给客户端的各个设备端
        for (LoginUserInfo loginUserInfo : toLoginUserInfos) {
            // 走消息传递,设置登录设备类型
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(loginUserInfo.getLoginServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(to, loginUserInfo.getDeviceEnum().getName()));
            } else {
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(loginUserInfo.getLoginServerAddress()));
            }
        }

    }
}
