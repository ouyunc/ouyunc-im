package com.ouyunc.im.processor;

import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.MessageEnum;
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
        EVENT_EXECUTORS.execute(() -> DbHelper.writeMessage(packet));
        Message message = (Message) packet.getMessage();
        // 消息发送方
        String from = message.getFrom();
        // 消息接收方
        String to = message.getTo();
        // ===================================做校验(@todo 这里可以改造，各种校验不通过后响应相关消息给客户端)=========================================
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
            // 下面是对集群以及qos消息可靠进行处理
            String from = message.getFrom();
            // 根据to从分布式缓存中取出targetServerAddress目标地址
            String to = message.getTo();
            // 将消息写到发件箱和及接收方的收件箱
            long timestamp = SystemClock.now();
            DbHelper.write2Timeline(packet, from, to, timestamp);
            // 发送给自己的其他端
            List<LoginUserInfo> fromLoginUserInfos = UserHelper.onlineAll(from, packet.getDeviceType());
            // 转发给自己客户端的各个设备端
            MessageHelper.send2MultiDevices(packet, fromLoginUserInfos);
            // 将其放入离线消息列表
            DbHelper.write2OfflineTimeline(packet, to, timestamp);
            // 获取该客户端在线的所有客户端，进行推送消息已读
            List<LoginUserInfo> toLoginUserInfos = UserHelper.onlineAll(to);
            if (CollectionUtils.isNotEmpty(toLoginUserInfos)) {
                // 存入离线消息，不以设备来区分
                MessageHelper.send2MultiDevices(packet, toLoginUserInfos);
            }
        });

    }
}
