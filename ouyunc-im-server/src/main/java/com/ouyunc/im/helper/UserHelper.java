package com.ouyunc.im.helper;


import cn.hutool.core.date.SystemClock;
import cn.hutool.json.JSONUtil;
import com.ouyunc.im.constant.CacheConstant;
import com.ouyunc.im.constant.IMConstant;
import com.ouyunc.im.constant.enums.*;
import com.ouyunc.im.context.IMServerContext;
import com.ouyunc.im.base.LoginUserInfo;
import com.ouyunc.im.packet.Packet;
import com.ouyunc.im.packet.message.Message;
import com.ouyunc.im.packet.message.content.LoginContent;
import com.ouyunc.im.utils.IdentityUtil;
import io.netty.channel.ChannelHandlerContext;
import io.netty.util.AttributeKey;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;


/**
 * @Author fangzhenxun
 * @Description: 用户的绑定，以及其他处理
 * @Version V3.0
 **/
public class UserHelper {
    private static Logger log = LoggerFactory.getLogger(UserHelper.class);


    /**
     * @Author fangzhenxun
     * @Description 绑定用户
     * @param ctx
     * @param packet
     * @return void
     */
    public static void bind(ChannelHandlerContext ctx, Packet packet) {
        final Message loginMessage = (Message) packet.getMessage();
        LoginContent loginContent = JSONUtil.toBean(loginMessage.getContent(), LoginContent.class);
        String identity = loginContent.getIdentity();
        byte deviceType = packet.getDeviceType();
        String comboIdentity = IdentityUtil.generalComboIdentity(identity, deviceType);
        AttributeKey<LoginUserInfo> channelTagLoginKey = AttributeKey.valueOf(IMConstant.CHANNEL_TAG_LOGIN);
        // 将用户绑定到channel中并打上tag标签
        LoginUserInfo loginUserInfo = new LoginUserInfo(loginContent.getIdentity(), IMServerContext.SERVER_CONFIG.getLocalServerAddress(), OnlineEnum.ONLINE, DeviceEnum.getDeviceEnumByValue(deviceType));
        ctx.channel().attr(channelTagLoginKey).set(loginUserInfo);
        // 存入用户登录信息
        IMServerContext.LOGIN_USER_INFO_CACHE.put(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + identity, loginUserInfo);
        // 存入本地用户注册表
        IMServerContext.USER_REGISTER_TABLE.put(comboIdentity, ctx);
    }

    /**
     * @Author fangzhenxun
     * @Description 用户解绑
     * @param comboIdentity
     * @param ctx
     * @return void
     */
    public static void unbind(String comboIdentity, ChannelHandlerContext ctx) {
        IMServerContext.LOGIN_USER_INFO_CACHE.delete(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
        ChannelHandlerContext ctx0 = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        IMServerContext.USER_REGISTER_TABLE.delete(comboIdentity);
        if (ctx == ctx0) {
            ctx0.close();
        }
        if (ctx != null) {
            ctx.close();
        }
        if (ctx0 != null) {
            ctx0.close();
        }
    }


    /**
     * @Author fangzhenxun
     * @Description 客户端做等待ack的队列处理 ，如果在一定时间内没有收到接收方返回的信息则重试发送信息（可能会导致重复接收，客户端需作去重处理）
     * @param from
     * @param to
     * @param packet
     * @return void
     */
    public static void doAck(String from, String to,  Packet packet) {
        // 3, 回执ack(AcknowledgeMessage)给发送方；
        Message acknowledgeMessage = new Message(to, from, MessageContentEnum.SERVER_REPLY_ACK_CONTENT.type(), JSONUtil.toJsonStr(packet), SystemClock.now());
        Packet<Message> ackPacket  = new Packet(packet.getProtocol(), packet.getProtocolVersion(), packet.getPacketId(), DeviceEnum.PC_OTHER.getValue(), NetworkEnum.OTHER.getValue(), IMServerContext.SERVER_CONFIG.getLocalHost(), MessageEnum.IM_REPLY_ACK.getValue(), packet.getEncryptType(), packet.getSerializeAlgorithm(),  acknowledgeMessage);
        // 异步直接发送
        MessageHelper.sendMessage(ackPacket, IdentityUtil.generalComboIdentity(from, packet.getDeviceType()));
    }



    /**
     * @Author fangzhenxun
     * @Description 判断用户是否在线,如果在线返回所有在线连接的服务器地址，支持多端登录
     * @param identity 用户登录唯一标识，手机号，邮箱，身份证号码等
     * @return String
     */
    public static List<LoginUserInfo> onlineAll(String identity) {
        List<LoginUserInfo> loginServerAddressList = new ArrayList<>();
        for (String supportOnlineDeviceName : IdentityUtil.supportOnlineLoginDevice()) {
            String comboIdentity = IdentityUtil.generalComboIdentity(identity, supportOnlineDeviceName);
            LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
            ChannelHandlerContext ctx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
            if (loginUserInfo != null && OnlineEnum.ONLINE.equals(loginUserInfo.getOnlineStatus()) && ctx != null) {
                loginServerAddressList.add(loginUserInfo);
            }
        }
        return loginServerAddressList;
    }

    /**
     * 获取某个端的登录信息
     * @param comboIdentity
     * @return
     */
    public static LoginUserInfo online(String comboIdentity) {
        LoginUserInfo loginUserInfo = IMServerContext.LOGIN_USER_INFO_CACHE.get(CacheConstant.OUYUNC + CacheConstant.IM_USER + CacheConstant.LOGIN + comboIdentity);
        ChannelHandlerContext ctx = IMServerContext.USER_REGISTER_TABLE.get(comboIdentity);
        if (loginUserInfo != null && OnlineEnum.ONLINE.equals(loginUserInfo.getOnlineStatus()) && ctx != null) {
            return loginUserInfo;
        }
        return null;
    }
}
