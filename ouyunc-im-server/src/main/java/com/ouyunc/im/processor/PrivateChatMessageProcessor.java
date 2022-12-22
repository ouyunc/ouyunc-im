package com.ouyunc.im.processor;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
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
        if (!MessageValidate.isAuth(from, packet.getDeviceType(), ctx) || MessageValidate.isBanned(from, IMConstant.USER_TYPE_1) || !MessageValidate.isFriend(from, to) || MessageValidate.isBackList(from, to, IMConstant.USER_TYPE_1) || MessageValidate.isShield(from, to, IMConstant.USER_TYPE_1)) {
            return;
        }
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
        fireProcess(ctx, packet,(ctx0, packet0)->{
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
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from, packet.getDeviceType());
            // 排除自己，发给其他端
            // 转发给自己客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            // 获取该客户端在线的所有客户端，进行推送消息已读
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
            if (CollectionUtil.isEmpty(toLoginUserInfos)) {
                // 存入离线消息，不以设备来区分
                DbHelper.addOfflineMessage(to, packet);
                return;
            }
            // 转发给某个客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
        });

    }
}
