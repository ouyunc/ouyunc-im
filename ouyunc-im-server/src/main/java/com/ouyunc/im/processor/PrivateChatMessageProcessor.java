package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.enums.MessageEnum;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.helper.DbHelper;
import com.ouyunc.im.helper.MessageHelper;
import com.ouyunc.im.helper.UserHelper;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.ExtraMessage;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.utils.IdentityUtil;
import com.ouyunc.im.utils.SocketAddressUtil;
import com.ouyunc.im.validate.MessageValidate;
import io.netty.channel.ChannelHandlerContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * 私聊消息处理器
 */
public class PrivateChatMessageProcessor extends AbstractMessageProcessor{
    private static Logger log = LoggerFactory.getLogger(PrivateChatMessageProcessor.class);


    @Override
    public MessageEnum messageType() {
        return MessageEnum.IM_PRIVATE_CHAT;
    }


    /**
     * 做权限，是否是好友，黑名单，被对方屏蔽等判断
     * @param ctx
     * @param packet
     */
    @Override
    public void preProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("PrivateChatMessageProcessor 正在前置处理 packet: {} ...", packet);
        // 存储packet到数据库中（目前只是保存相关信息，不做扩展，以后可以做数据分析使用）
        EVENT_EXECUTORS.execute(() -> DbHelper.addMessage(packet));
        Message message = (Message) packet.getMessage();
        // 消息发送方
        String from = message.getFrom();
        // 消息接收方
        String to = message.getTo();
        // ===================================做权限校验=========================================
        if (!MessageValidate.isAuth(from, packet.getDeviceType(), ctx)) {
            return;
        }
        // 是否被平台封禁
//        if (VerifyUtil.isBanned(from, IMConstant.IDENTITY_TYPE_1)) {
//            return;
//        }
//        // 如果开启认证校验，认证不通过则不做处理
//        if (!VerifyUtil.hasPermission(from, IMConstant.IDENTITY_TYPE_1, null)) {
//            return;
//        }
//        // 如果开启认证校验，认证不通过则不做处理
//        if (!VerifyUtil.isFriend(from, to)) {
//            return;
//        }
//        // 如果开启认证校验，认证不通过则不做处理
//        if (VerifyUtil.isBackList(from, to, IMConstant.IDENTITY_TYPE_1)) {
//            return;
//        }
//        // 如果开启认证校验，认证不通过则不做处理
//        if (VerifyUtil.isShield(from, to, IMConstant.IDENTITY_TYPE_1)) {
//            return;
//        }

        // 交给下个处理
        ctx.fireChannelRead(packet);
    }



    /**
     * 私聊信息处理
     * @param ctx
     * @param packet
     */
    @Override
    public void doProcess(ChannelHandlerContext ctx, Packet packet) {
        log.info("PrivateChatMessageProcessor 正在处理私聊消息packet: {}",packet);
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
        // 发送给自己的其他端
        List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from);
        // 排除自己，发给其他端
        for (LoginUserInfo fromLoginUserInfo : fromLoginUserInfos) {// 排除自己发给其他端
            if (!IdentityUtil.generalComboIdentity(from, packet.getDeviceType()).equals(IdentityUtil.generalComboIdentity(fromLoginUserInfo.getIdentity(), fromLoginUserInfo.getDeviceEnum().getName()))) {
                // 走消息传递,设置登录设备类型
                if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(fromLoginUserInfo.getLoginServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                    MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(from, fromLoginUserInfo.getDeviceEnum().getName()));
                } else {
                    MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(fromLoginUserInfo.getLoginServerAddress()));
                }
            }
        }
        // 获取该客户端在线的所有客户端，进行推送消息已读
        List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
        if (CollectionUtil.isEmpty(toLoginUserInfos)) {
            // 存入离线消息
            DbHelper.addOfflineMessage(to,packet);
            return;
        }
        // 转发给某个客户端的各个设备端
        for (LoginUserInfo loginUserInfo : toLoginUserInfos) {
            // 走消息传递,设置登录设备类型
            if (IMServerContext.SERVER_CONFIG.getLocalServerAddress().equals(loginUserInfo.getLoginServerAddress()) || !IMServerContext.SERVER_CONFIG.isClusterEnable()) {
                MessageHelper.sendMessage(packet, IdentityUtil.generalComboIdentity(from, loginUserInfo.getDeviceEnum().getName()));
            } else {
                MessageHelper.deliveryMessage(packet, SocketAddressUtil.convert2SocketAddress(loginUserInfo.getLoginServerAddress()));
            }
        }

    }
}
